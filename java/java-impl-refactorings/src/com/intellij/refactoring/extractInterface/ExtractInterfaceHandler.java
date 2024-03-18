// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractInterface;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExtractInterfaceHandler implements ElementsHandler, ContextAwareActionHandler {

  private PsiClass myClass;
  private String myInterfaceName;
  private MemberInfo[] mySelectedMembers;
  private PsiDirectory myTargetDir;
  private DocCommentPolicy myJavaDocPolicy;

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    return RefactoringActionContextUtil.isOutsideModuleAndCodeBlock(editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

    List<PsiMember> elements = CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, file, null, e -> {
      return e instanceof PsiMember member && !(member.getContainingClass() instanceof PsiAnonymousClass);
    });
    if (elements.isEmpty()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.EXTRACT_INTERFACE);
      return;
    }
    invoke(project, elements.toArray(PsiElement.EMPTY_ARRAY), dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    PsiElement parent = PsiTreeUtil.findCommonParent(elements);
    myClass = parent instanceof PsiClass aClass
              ? aClass
              : PsiTreeUtil.getParentOfType(parent, PsiClass.class, false);
    if (myClass == null) {
      String message = RefactoringBundle.message("error.select.class.to.be.refactored");
      CommonRefactoringUtil.showErrorHint(project, null, message, getRefactoringName(), HelpID.EXTRACT_INTERFACE);
      return;
    }

    if (myClass instanceof PsiImplicitClass) {
      String message = RefactoringBundle.message("error.interface.cannot.be.extracted.from.implicit.class");
      CommonRefactoringUtil.showErrorHint(project, null, message, getRefactoringName(), HelpID.EXTRACT_INTERFACE);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, myClass)) return;

    final Set<PsiElement> selectedMembers = new HashSet<>();
    Collections.addAll(selectedMembers, elements);
    final ExtractInterfaceDialog dialog = new ExtractInterfaceDialog(project, myClass, selectedMembers);
    if (!dialog.showAndGet() || !dialog.isExtractSuperclass()) {
      return;
    }

    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    ExtractSuperClassUtil.checkSuperAccessible(dialog.getTargetDirectory(), conflicts, myClass);
    if (!ExtractSuperClassUtil.showConflicts(dialog, conflicts, project)) return;

    PsiClass anInterface = WriteCommandAction
      .writeCommandAction(project)
      .withName(getRefactoringName())
      .compute(() -> {
        myInterfaceName = dialog.getExtractedSuperName();
        mySelectedMembers = dialog.getSelectedMemberInfos().toArray(new MemberInfo[0]);
        myTargetDir = dialog.getTargetDirectory();
        myJavaDocPolicy = new DocCommentPolicy(dialog.getDocCommentPolicy());
        LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
        try {
          return extractInterface(myTargetDir, myClass, myInterfaceName, mySelectedMembers, myJavaDocPolicy);
        }
        finally {
          a.finish();
        }
      });
    ExtractClassUtil.askAndTurnRefsToSuper(myClass, anInterface);
  }

  static PsiClass extractInterface(PsiDirectory targetDir,
                                   PsiClass aClass,
                                   String interfaceName,
                                   MemberInfo[] selectedMembers,
                                   DocCommentPolicy javaDocPolicy) {
    final Project project = aClass.getProject();
    project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
      .refactoringStarted(ExtractSuperClassUtil.REFACTORING_EXTRACT_SUPER_ID, ExtractSuperClassUtil.createBeforeData(aClass, selectedMembers));
    final PsiClass anInterface = JavaDirectoryService.getInstance().createInterface(targetDir, interfaceName);
    try {
      PsiJavaCodeReferenceElement ref = ExtractSuperClassUtil.createExtendingReference(anInterface, aClass, selectedMembers);
      final PsiReferenceList referenceList = aClass.isInterface() ? aClass.getExtendsList() : aClass.getImplementsList();
      assert referenceList != null;
      CodeStyleManager.getInstance(project).reformat(referenceList.add(ref));
      PullUpProcessor pullUpHelper = new PullUpProcessor(aClass, anInterface, selectedMembers, javaDocPolicy);
      pullUpHelper.moveMembersToBase();
      return anInterface;
    }
    finally {
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .refactoringDone(ExtractSuperClassUtil.REFACTORING_EXTRACT_SUPER_ID, ExtractSuperClassUtil.createAfterData(anInterface));
    }
  }

  @Nls
  private String getCommandName() {
    return RefactoringBundle.message("extract.interface.command.name", myInterfaceName, DescriptiveNameUtil.getDescriptiveName(myClass));
  }

  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    return ContainerUtil.exists(elements, element -> element instanceof PsiMember);
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("extract.interface.title");
  }
}