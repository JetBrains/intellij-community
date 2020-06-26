// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.JavaBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class SuperMethodWarningUtil {
  public static final Key<PsiMethod[]> SIBLINGS = Key.create("MULTIPLE_INHERITANCE");
  private SuperMethodWarningUtil() {}

  public static PsiMethod @NotNull [] checkSuperMethods(@NotNull PsiMethod method, @NotNull String actionString) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return checkSuperMethods(method, actionString, Collections.emptyList());
  }

  public static PsiMethod @NotNull [] getTargetMethodCandidates(@NotNull PsiMethod method, @NotNull Collection<? extends PsiElement> ignore) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};

    final Collection<PsiMethod> superMethods = getSuperMethods(method, aClass, ignore);
    if (superMethods.isEmpty()) return new PsiMethod[]{method};
    return superMethods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public static PsiMethod @NotNull [] checkSuperMethods(@NotNull PsiMethod method, @NotNull String actionString, @NotNull Collection<? extends PsiElement> ignore) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiMethod[] methodTargetCandidates = getTargetMethodCandidates(method, ignore);
    if (methodTargetCandidates.length == 1 && methodTargetCandidates[0] == method) return methodTargetCandidates;

    Set<String> superClasses = new HashSet<>();
    boolean superAbstract = false;
    boolean parentInterface = false;
    for (final PsiMethod superMethod : methodTargetCandidates) {
      final PsiClass containingClass = superMethod.getContainingClass();
      superClasses.add(containingClass.getQualifiedName());
      final boolean isInterface = containingClass.isInterface();
      superAbstract |= isInterface || superMethod.hasModifierProperty(PsiModifier.ABSTRACT);
      parentInterface |= isInterface;
    }

    int shouldIncludeBase = showDialog(
      method.getProject(),
      DescriptiveNameUtil.getDescriptiveName(method),
      actionString,
      superAbstract,
      parentInterface,
      method.getContainingClass().isInterface(),
      ArrayUtilRt.toStringArray(superClasses));
    switch (shouldIncludeBase) {
      case Messages.YES:
        return methodTargetCandidates;
      case Messages.NO:
        return new PsiMethod[] {method};
      default:
        return PsiMethod.EMPTY_ARRAY;
    }
  }

  @NotNull
  static Collection<PsiMethod> getSuperMethods(@NotNull PsiMethod method, PsiClass aClass, @NotNull Collection<? extends PsiElement> ignore) {
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
        }, JavaBundle.message("progress.title.searching.for.sub.classes"), true, aClass.getProject())) {
          throw new ProcessCanceledException();
        }
        if (siblingSuperMethod[0] != null) {
          superMethods.add(siblingSuperMethod[0]);
          superMethods.add(method); // add original method too because sometimes FindUsages can't find usages of this method by sibling super method
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

    int useSuperMethod = showDialog(
      method.getProject(),
      DescriptiveNameUtil.getDescriptiveName(method),
      actionString,
      containingClass.isInterface() || superMethod.hasModifierProperty(PsiModifier.ABSTRACT),
      containingClass.isInterface(),
      aClass.isInterface(),
      containingClass.getQualifiedName()
    );
    switch (useSuperMethod) {
      case Messages.YES: return superMethod;
      case Messages.NO: return method;
      default: return null;
    }
  }

  public static void checkSuperMethod(@NotNull PsiMethod method,
                                      @NotNull String actionString,
                                      @NotNull final PsiElementProcessor<? super PsiMethod> processor,
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
    String title = method.getName() +
                   (superMethods.length > 1 ? " has super methods"
                                            : (containingClass.isInterface() && !aClass.isInterface() ? " implements"
                                                                                                      : " overrides") +
                                              " method of " + SymbolPresentationUtil.getSymbolPresentableText(containingClass));
    JBPopupFactory.getInstance().createPopupChooserBuilder(ContainerUtil.newArrayList(renameBase, renameCurrent))
      .setTitle(title)
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChosenCallback(value -> {
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
      })
      .createPopup().showInBestPositionFor(editor);
  }

  @Messages.YesNoCancelResult
  private static int showDialog(@NotNull Project project,
                                @NotNull String name,
                                @NotNull String actionString,
                                boolean isSuperAbstract,
                                boolean isParentInterface,
                                boolean isContainedInInterface,
                                String @NotNull ... classNames) {
    String message = getDialogMessage(name, actionString, isSuperAbstract, isParentInterface, isContainedInInterface, classNames);
    return Messages.showYesNoCancelDialog(project,
                                          message, JavaBundle.message("dialog.title.super.method.found"), JavaBundle.message("button.base.method"),
                                          JavaBundle.message("button.current.method"),
                                          Messages.getCancelButton(), Messages.getQuestionIcon());
  }

  @NotNull
  private static String getDialogMessage(@NotNull String name,
                                         @NotNull String actionString,
                                         boolean isSuperAbstract,
                                         boolean isParentInterface,
                                         boolean isContainedInInterface,
                                         String @NotNull [] classNames) {
    StringBuilder labelText = new StringBuilder();
    String classType = isParentInterface ? JavaBundle.message("element.of.interface") : JavaBundle.message("element.of.class");
    String methodString = JavaPsiBundle.message("element.method");
    labelText.append("<html>").append(JavaBundle.message("label.method", name)).append("<br>");
    if (classNames.length == 1) {
      final String className = classNames[0];
      labelText.append(isContainedInInterface || !isSuperAbstract
                       ? JavaBundle.message("label.overrides.method.of_class_or_interface.name", methodString, classType, className)
                       : JavaBundle.message("label.implements.method.of_class_or_interface.name", methodString, classType, className));
      labelText.append("<br>");
    }
    else {
      labelText.append(JavaBundle.message("label.implements.method.of_interfaces"));

      for (final String className : classNames) {
        labelText.append("<br>&emsp;'").append(className).append("'");
      }
    }

    labelText.append("<br>");
    labelText.append(JavaBundle.message("prompt.do.you.want.to.action_verb.the.method.from_class", actionString, classNames.length));
    return labelText.toString();
  }
}