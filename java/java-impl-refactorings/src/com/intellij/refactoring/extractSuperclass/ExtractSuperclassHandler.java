// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractSuperclass;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.refactoring.extractInterface.ExtractClassUtil;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExtractSuperclassHandler implements ElementsHandler, ExtractSuperclassDialog.Callback, ContextAwareActionHandler {

  private PsiClass mySubclass;
  private Project myProject;

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    return RefactoringActionContextUtil.isOutsideModuleAndCodeBlock(editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    List<PsiElement> elements = CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, file, null, e -> e instanceof PsiMember);
    if (elements.isEmpty()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.EXTRACT_SUPERCLASS);
      return;
    }
    invoke(project, elements.toArray(PsiElement.EMPTY_ARRAY), dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    myProject = project;
    PsiElement parent = PsiTreeUtil.findCommonParent(elements);
    mySubclass = parent instanceof PsiClass aClass
              ? aClass
              : PsiTreeUtil.getParentOfType(parent, PsiClass.class, false);
    if (mySubclass == null) {
      String message = RefactoringBundle.message("error.select.class.to.be.refactored");
      CommonRefactoringUtil.showErrorHint(project, null, message, getRefactoringName(), HelpID.EXTRACT_SUPERCLASS);
      return;
    }

    if (mySubclass instanceof PsiImplicitClass) {
      String message = RefactoringBundle.message("error.superclass.cannot.be.extracted.from.implicit.class");
      CommonRefactoringUtil.showErrorHint(project, null, message, getRefactoringName(), HelpID.EXTRACT_SUPERCLASS);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, mySubclass)) return;

    Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    if (mySubclass.isInterface()) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("superclass.cannot.be.extracted.from.an.interface"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.EXTRACT_SUPERCLASS);
      return;
    }

    if (mySubclass.isEnum()) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("superclass.cannot.be.extracted.from.an.enum"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.EXTRACT_SUPERCLASS);
      return;
    }

    if (mySubclass.isRecord()) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("superclass.cannot.be.extracted.from.a.record"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.EXTRACT_SUPERCLASS);
      return;
    }

    List<MemberInfo> memberInfos = MemberInfo.extractClassMembers(mySubclass, new MemberInfo.Filter<>() {
      @Override
      public boolean includeMember(PsiMember element) {
        return true;
      }
    }, false);
    final Set<PsiElement> selectedMembers = new HashSet<>();
    Collections.addAll(selectedMembers, elements);
    for (MemberInfo info : memberInfos) {
      if (selectedMembers.contains(info.getMember())) {
        info.setChecked(true);
      }
    }

    if (mySubclass instanceof PsiAnonymousClass) {
      memberInfos = ContainerUtil.filter(memberInfos, memberInfo -> !(memberInfo.getMember() instanceof PsiClass &&
                                                                      memberInfo.getOverrides() != null));
    }

    final ExtractSuperclassDialog dialog = new ExtractSuperclassDialog(project, mySubclass, memberInfos, this);
    if (!dialog.showAndGet() || !dialog.isExtractSuperclass()) {
      return;
    }

    PsiClass superClass = WriteCommandAction
      .writeCommandAction(project)
      .withName(getRefactoringName())
      .compute(() -> doRefactoring(project, mySubclass, dialog));
    ExtractClassUtil.askAndTurnRefsToSuper(mySubclass, superClass);
  }

  @Override
  public boolean checkConflicts(final ExtractSuperclassDialog dialog) {
    final MemberInfo[] infos = dialog.getSelectedMemberInfos().toArray(new MemberInfo[0]);
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    final PsiPackage targetPackage = targetDirectory != null ? JavaDirectoryService.getInstance().getPackage(targetDirectory) : null;
    final MultiMap<PsiElement,String> conflicts = new MultiMap<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
      final PsiClass superClass =
        mySubclass.getExtendsListTypes().length > 0 || mySubclass instanceof PsiAnonymousClass ? mySubclass.getSuperClass() : null;
      if (targetPackage != null) {
        conflicts.putAllValues(PullUpConflictsUtil.checkConflicts(
          infos, mySubclass, superClass, targetPackage, targetDirectory, dialog.getContainmentVerifier(), false));
      }
    }), RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) return false;
    ExtractSuperClassUtil.checkSuperAccessible(targetDirectory, conflicts, mySubclass);
    return ExtractSuperClassUtil.showConflicts(dialog, conflicts, myProject);
  }

  // invoked inside Command and Atomic action
  private static PsiClass doRefactoring(final Project project, final PsiClass subclass, final ExtractSuperclassDialog dialog) {
    final String superclassName = dialog.getExtractedSuperName();
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    final MemberInfo[] selectedMemberInfos = dialog.getSelectedMemberInfos().toArray(new MemberInfo[0]);
    final DocCommentPolicy javaDocPolicy = new DocCommentPolicy(dialog.getDocCommentPolicy());
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName(subclass, superclassName));
    try {
      return ExtractSuperClassUtil.extractSuperClass(project, targetDirectory, superclassName, subclass, selectedMemberInfos, javaDocPolicy);
    }
    finally {
      a.finish();
    }
  }

  @NlsContexts.Label
  private static String getCommandName(final PsiClass subclass, String newName) {
    return RefactoringBundle.message("extract.superclass.command.name", newName, DescriptiveNameUtil.getDescriptiveName(subclass));
  }

  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    return ContainerUtil.exists(elements, element -> element instanceof PsiMember);
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("extract.superclass.title");
  }
}