/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SuperMethodWarningUtil {
  public static Key<PsiMethod[]> SIBLINGS = Key.create("MULTIPLE_INHERITANCE");
  private SuperMethodWarningUtil() {}

  @NotNull
  public static PsiMethod[] checkSuperMethods(@NotNull PsiMethod method, @NotNull String actionString) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return checkSuperMethods(method, actionString, Collections.emptyList());
  }

  @NotNull
  public static PsiMethod[] checkSuperMethods(@NotNull PsiMethod method, @NotNull String actionString, @NotNull Collection<PsiElement> ignore) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};

    final Collection<PsiMethod> superMethods = getSuperMethods(method, aClass, ignore);
    if (superMethods.isEmpty()) return new PsiMethod[]{method};


    Set<String> superClasses = new HashSet<>();
    boolean superAbstract = false;
    boolean parentInterface = false;
    for (final PsiMethod superMethod : superMethods) {
      final PsiClass containingClass = superMethod.getContainingClass();
      superClasses.add(containingClass.getQualifiedName());
      final boolean isInterface = containingClass.isInterface();
      superAbstract |= isInterface || superMethod.hasModifierProperty(PsiModifier.ABSTRACT);
      parentInterface |= isInterface;
    }

    SuperMethodWarningDialog dialog =
        new SuperMethodWarningDialog(method.getProject(), DescriptiveNameUtil.getDescriptiveName(method), actionString, superAbstract,
                                     parentInterface, aClass.isInterface(), ArrayUtil.toStringArray(superClasses));
    dialog.show();

    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      return superMethods.toArray(new PsiMethod[superMethods.size()]);
    }
    if (dialog.getExitCode() == SuperMethodWarningDialog.NO_EXIT_CODE) {
      return new PsiMethod[]{method};
    }

    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  static Collection<PsiMethod> getSuperMethods(@NotNull PsiMethod method, PsiClass aClass, @NotNull Collection<PsiElement> ignore) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    final Collection<PsiMethod> superMethods = DeepestSuperMethodsSearch.search(method).findAll();
    superMethods.removeAll(ignore);

    if (superMethods.isEmpty()) {
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(aClass);
      if (virtualFile != null && ProjectRootManager.getInstance(aClass.getProject()).getFileIndex().isInSourceContent(virtualFile)) {
        PsiMethod[] siblingSuperMethod = new PsiMethod[1];
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(()->{
          siblingSuperMethod[0] = ReadAction.compute(()->FindSuperElementsHelper.getSiblingInheritedViaSubClass(method));
        }, "Searching for sub-classes", true, aClass.getProject())) {
          throw new ProcessCanceledException();
        }
        if (siblingSuperMethod[0] != null) {
          superMethods.add(siblingSuperMethod[0]);
        }
      }
    }
    return superMethods;
  }


  public static PsiMethod checkSuperMethod(@NotNull PsiMethod method, @NotNull String actionString) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return method;

    PsiMethod superMethod = method.findDeepestSuperMethod();
    if (superMethod == null) return method;

    if (ApplicationManager.getApplication().isUnitTestMode()) return superMethod;

    PsiClass containingClass = superMethod.getContainingClass();

    SuperMethodWarningDialog dialog =
        new SuperMethodWarningDialog(
            method.getProject(),
            DescriptiveNameUtil.getDescriptiveName(method), actionString, containingClass.isInterface() || superMethod.hasModifierProperty(PsiModifier.ABSTRACT),
            containingClass.isInterface(), aClass.isInterface(), containingClass.getQualifiedName()
        );
    dialog.show();

    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) return superMethod;
    if (dialog.getExitCode() == SuperMethodWarningDialog.NO_EXIT_CODE) return method;

    return null;
  }

  public static void checkSuperMethod(@NotNull PsiMethod method,
                                      @NotNull String actionString,
                                      @NotNull final PsiElementProcessor<PsiMethod> processor,
                                      @NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      processor.execute(method);
      return;
    }

    PsiMethod[] superMethods = method.findDeepestSuperMethods();
    if (superMethods.length == 0) {
      processor.execute(method);
      return;
    }

    final PsiClass containingClass = superMethods[0].getContainingClass();
    if (containingClass == null) {
      processor.execute(method);
      return;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      processor.execute(superMethods[0]);
      return;
    }

    final PsiMethod[] methods = {superMethods[0], method};
    final String renameBase = actionString + " base method" + (superMethods.length > 1 ? "s" : "");
    final String renameCurrent = actionString + " only current method";
    final JBList<String> list = new JBList<>(renameBase, renameCurrent);
    String title = method.getName() +
                   (superMethods.length > 1 ? " has super methods" 
                                            : (containingClass.isInterface() && !aClass.isInterface() ? " implements" 
                                                                                                      : " overrides") + 
                                              " method of " + SymbolPresentationUtil.getSymbolPresentableText(containingClass));
    JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setTitle(title)
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChoosenCallback(() -> {
        final Object value = list.getSelectedValue();
        if (value != null) {
          if (value.equals(renameBase)) {
            try {
              methods[0].putUserData(SIBLINGS, superMethods);
              processor.execute(methods[0]);
            }
            finally {
              methods[0].putUserData(SIBLINGS, null);
            }
          }
          else {
            processor.execute(methods[1]);
          }
        }
      }).createPopup().showInBestPositionFor(editor);
  }

  @Messages.YesNoCancelResult
  public static int askWhetherShouldAnnotateBaseMethod(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    String implement = !method.hasModifierProperty(PsiModifier.ABSTRACT) && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)
                  ? InspectionsBundle.message("inspection.annotate.quickfix.implements")
                  : InspectionsBundle.message("inspection.annotate.quickfix.overrides");
    String message = InspectionsBundle.message("inspection.annotate.quickfix.overridden.method.messages",
                                               DescriptiveNameUtil.getDescriptiveName(method), implement,
                                               DescriptiveNameUtil.getDescriptiveName(superMethod));
    String title = InspectionsBundle.message("inspection.annotate.quickfix.overridden.method.warning");
    return Messages.showYesNoCancelDialog(method.getProject(), message, title, "Annotate", "Don't Annotate", CommonBundle.getCancelButtonText(), Messages.getQuestionIcon());
  }
}