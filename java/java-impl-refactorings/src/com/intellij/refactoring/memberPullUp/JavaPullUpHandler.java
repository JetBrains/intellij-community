// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPullUp;

import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaPullUpHandler implements PullUpDialog.Callback, ElementsHandler, ContextAwareActionHandler, JavaPullUpHandlerBase {
  private PsiClass mySubclass;
  private Project myProject;

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    List<PsiElement> elements =
      CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, file, PsiCodeBlock.class, e -> e instanceof PsiMember);
    if (elements.isEmpty()) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(elements.get(0), PsiClass.class, false);
    if (psiClass == null) return false;
    return psiClass instanceof PsiAnonymousClass || RefactoringActionContextUtil.isClassWithExtendsOrImplements(psiClass);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

    List<PsiElement> elements = CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, file, null, e -> e instanceof PsiMember);
    if (elements.isEmpty()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.inside.a.class.to.pull.members.from"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MEMBERS_PULL_UP);
    }
    else {
      invoke(project, elements.toArray(PsiElement.EMPTY_ARRAY), dataContext);
    }
  }

  @Override
  public void runSilently(@NotNull PsiClass sourceClass,
                          PsiClass targetSuperClass,
                          MemberInfo[] membersToMove, DocCommentPolicy javaDocPolicy) {
    new PullUpProcessor(sourceClass, targetSuperClass, membersToMove, javaDocPolicy).run();
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    myProject = project;
    PsiElement parent = PsiTreeUtil.findCommonParent(elements);
    PsiClass aClass = parent instanceof PsiClass parentClass
                 ? parentClass
                 : PsiTreeUtil.getParentOfType(parent, PsiClass.class, false);
    if (aClass == null) {
      String message = RefactoringBundle.message("error.select.class.to.be.refactored");
      CommonRefactoringUtil.showErrorHint(project, null, message, getRefactoringName(), HelpID.MEMBERS_PULL_UP);
      return;
    }
    final Set<PsiElement> selectedMembers = new HashSet<>();
    Collections.addAll(selectedMembers, elements);
    invoke(dataContext, aClass, selectedMembers);
  }

  private void invoke(DataContext dataContext, PsiClass aClass, Set<PsiElement> selectedMembers) {
    final Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    if (aClass == null) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context",
                                                                             getRefactoringName()));
      CommonRefactoringUtil.showErrorHint(myProject, editor, message, getRefactoringName(), HelpID.MEMBERS_PULL_UP);
      return;
    }

    List<PsiClass> bases = RefactoringHierarchyUtil.createBasesList(aClass, false, true);
    if (bases.isEmpty()) {
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) {
        invoke(dataContext, containingClass, Set.of(aClass));
        return;
      }
      String message = RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("class.does.not.have.base.classes.interfaces.in.current.project", aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorHint(myProject, editor, message, getRefactoringName(), HelpID.MEMBERS_PULL_UP);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, aClass)) return;
    mySubclass = aClass;
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(mySubclass, element -> {
      if (mySubclass.isEnum()) {
        if (element instanceof PsiMethod method && PullUpDialog.isEnumSyntheticMethod(method)) {
          return false;
        }
        else if (element instanceof PsiEnumConstant || element instanceof PsiClassInitializer) {
          return false;
        }
        else if (element instanceof PsiField field && !field.hasModifierProperty(PsiModifier.STATIC)) {
          return false;
        }
      }
      return true;
    });
    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(mySubclass);

    for (MemberInfo memberInfo : members) {
      if (selectedMembers.contains(memberInfo.getMember())) {
        memberInfo.setChecked(true);
      }
    }

    new PullUpDialog(myProject, aClass, bases, memberInfoStorage, this).show();
  }

  @Override
  public boolean checkConflicts(final PullUpDialog dialog) {
    final List<MemberInfo> infos = dialog.getSelectedMemberInfos();
    final MemberInfo[] memberInfos = infos.toArray(new MemberInfo[0]);
    final PsiClass superClass = dialog.getSuperClass();
    if (superClass == null || !checkWritable(superClass, memberInfos)) return false;
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
      final PsiDirectory targetDirectory = superClass.getContainingFile().getContainingDirectory();
      final PsiPackage targetPackage = targetDirectory != null ? JavaDirectoryService.getInstance().getPackage(targetDirectory) : null;
      if (targetDirectory != null && targetPackage != null) {
        conflicts.putAllValues(PullUpConflictsUtil.checkConflicts(memberInfos, mySubclass, superClass, targetPackage, targetDirectory, dialog.getContainmentVerifier()));
      }
    }), RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) return false;
    if (!conflicts.isEmpty()) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts, () ->
        new PullUpProcessor(mySubclass, superClass, infos.toArray(new MemberInfo[0]), new DocCommentPolicy(dialog.getJavaDocPolicy())).run());
      boolean ok = conflictsDialog.showAndGet();
      if (!ok && conflictsDialog.isShowConflicts()) dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      return ok;
    }
    return true;
  }

  private boolean checkWritable(PsiClass superClass, MemberInfo[] infos) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, superClass)) return false;
    for (MemberInfo info : infos) {
      if (info.getMember() instanceof PsiClass && info.getOverrides() != null) continue;
      if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, info.getMember())) return false;
    }
    return true;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    return ContainerUtil.exists(elements, element -> element instanceof PsiMember);
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("pull.members.up.title");
  }
}