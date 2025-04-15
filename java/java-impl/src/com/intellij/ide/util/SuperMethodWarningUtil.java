// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
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
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class SuperMethodWarningUtil {
  public static final Key<PsiMethod[]> SIBLINGS = Key.create("MULTIPLE_INHERITANCE");
  private SuperMethodWarningUtil() {}

  public static PsiMethod @NotNull [] checkSuperMethods(@NotNull PsiMethod method, @NotNull String actionString) {
    ThreadingAssertions.assertEventDispatchThread();
    return checkSuperMethods(method, actionString, Collections.emptyList());
  }

  public static PsiMethod @NotNull [] getTargetMethodCandidates(@NotNull PsiMethod method, @NotNull Collection<? extends PsiElement> ignore) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};

    final Collection<PsiMethod> superMethods = getSuperMethods(method, aClass, ignore);
    if (superMethods.isEmpty()) return new PsiMethod[]{method};
    return superMethods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public static PsiMethod @NotNull [] checkSuperMethods(@NotNull PsiMethod method, @NotNull Collection<? extends PsiElement> ignore) {
    return checkSuperMethods(method, null, ignore);
  }

  public static PsiMethod @NotNull [] checkSuperMethods(@NotNull PsiMethod method,
                                                        @NlsSafe @Nullable String actionString,
                                                        @NotNull Collection<? extends PsiElement> ignore) {
    ThreadingAssertions.assertEventDispatchThread();
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
    return switch (shouldIncludeBase) {
      case Messages.YES -> methodTargetCandidates;
      case Messages.NO -> new PsiMethod[]{method};
      default -> PsiMethod.EMPTY_ARRAY;
    };
  }

  static @NotNull Collection<PsiMethod> getSuperMethods(@NotNull PsiMethod method, PsiClass aClass, @NotNull Collection<? extends PsiElement> ignore) {
    ThreadingAssertions.assertEventDispatchThread();
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    Collection<PsiMethod> superMethods = new ArrayList<>(DeepestSuperMethodsSearch.search(method).findAll());
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


  public static PsiMethod checkSuperMethod(@NotNull PsiMethod method) {
    ThreadingAssertions.assertEventDispatchThread();
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return method;

    PsiMethod superMethod = method.findDeepestSuperMethod();
    if (superMethod == null) return method;

    if (ApplicationManager.getApplication().isUnitTestMode()) return superMethod;

    PsiClass containingClass = superMethod.getContainingClass();

    int useSuperMethod = showDialog(
      method.getProject(),
      RefactoringBundle.message("to.refactor"),
      DescriptiveNameUtil.getDescriptiveName(method),
      containingClass.isInterface() || superMethod.hasModifierProperty(PsiModifier.ABSTRACT),
      containingClass.isInterface(),
      aClass.isInterface(),
      containingClass.getQualifiedName()
    );
    return switch (useSuperMethod) {
      case Messages.YES -> superMethod;
      case Messages.NO -> method;
      default -> null;
    };
  }

  public static void checkSuperMethod(@NotNull PsiMethod method,
                                      final @NotNull PsiElementProcessor<? super PsiMethod> processor,
                                      @NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
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
    final String renameBase = JavaRefactoringBundle.message("refactor.base.method.choice", superMethods.length > 1 ? 0 : 1);
    final String renameCurrent = JavaRefactoringBundle.message("refactor.only.current.method.choice");
    String title;
    if (superMethods.length > 1) {
      title = JavaBundle.message("rename.super.methods.chooser.popup.title", method.getName());
    }
    else {
      title = JavaBundle.message("rename.super.base.chooser.popup.title", 
                                 method.getName(), 
                                 containingClass.isInterface() && !aClass.isInterface() ? 0 : 1, 
                                 SymbolPresentationUtil.getSymbolPresentableText(containingClass));
    }
    JBPopupFactory.getInstance().createPopupChooserBuilder(List.of(renameBase, renameCurrent))
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
                                @NlsSafe @Nullable String actionString,
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

  private static @Nls @NotNull String getDialogMessage(@NotNull String name,
                                                       @NlsSafe @Nullable String actionString,
                                                       boolean isSuperAbstract,
                                                       boolean isParentInterface,
                                                       boolean isContainedInInterface,
                                                       String @NotNull [] classNames) {
    HtmlBuilder labelText = new HtmlBuilder();
    int classType = isParentInterface ? 0 : 1;
    labelText.append(JavaBundle.message("label.method", name)).br();
    if (classNames.length == 1) {
      final String className = classNames[0];
      labelText.append(isContainedInInterface || !isSuperAbstract
                       ? JavaBundle.message("label.overrides.method.of_class_or_interface.name", classType, className)
                       : JavaBundle.message("label.implements.method.of_class_or_interface.name", classType, className));
      labelText.br();
    }
    else {
      labelText.append(JavaBundle.message("label.implements.method.of_interfaces"));

      for (final @NlsSafe String className : classNames) {
        labelText.br().nbsp(2).append("'").append(className).append("'");
      }
    }

    labelText.br();
    labelText.append(HtmlChunk.text(JavaBundle.message("prompt.do.you.want.to.action_verb.the.method.from_class", 
                                                       classNames.length, 
                                                       ObjectUtils.notNull(actionString, RefactoringBundle.message("to.refactor")))));
    return labelText.wrapWithHtmlBody().toString();
  }
}