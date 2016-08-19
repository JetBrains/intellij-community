/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.refactoring.extractSuperclass;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractInterface.ExtractClassUtil;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class ExtractSuperclassHandler implements RefactoringActionHandler, ExtractSuperclassDialog.Callback, ElementsHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractSuperclass.ExtractSuperclassHandler");

  public static final String REFACTORING_NAME = RefactoringBundle.message("extract.superclass.title");

  private PsiClass mySubclass;
  private Project myProject;


  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_SUPERCLASS);
        return;
      }
      if (element instanceof PsiClass) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    myProject = project;
    mySubclass = (PsiClass)elements[0];

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, mySubclass)) return;

    Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    if (mySubclass.isInterface()) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("superclass.cannot.be.extracted.from.an.interface"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_SUPERCLASS);
      return;
    }

    if (mySubclass.isEnum()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("superclass.cannot.be.extracted.from.an.enum"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_SUPERCLASS);
      return;
    }


    List<MemberInfo> memberInfos = MemberInfo.extractClassMembers(mySubclass, new MemberInfo.Filter<PsiMember>() {
      @Override
      public boolean includeMember(PsiMember element) {
        return true;
      }
    }, false);

    if (mySubclass instanceof PsiAnonymousClass) {
      memberInfos = ContainerUtil.filter(memberInfos, memberInfo -> !(memberInfo.getMember() instanceof PsiClass &&
                                                                      memberInfo.getOverrides() != null));
    }

    final ExtractSuperclassDialog dialog =
      new ExtractSuperclassDialog(project, mySubclass, memberInfos, this);
    if (!dialog.showAndGet() || !dialog.isExtractSuperclass()) {
      return;
    }

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final Runnable action = () -> doRefactoring(project, mySubclass, dialog);
      ApplicationManager.getApplication().runWriteAction(action);
    }, REFACTORING_NAME, null);
  }

  @Override
  public boolean checkConflicts(final ExtractSuperclassDialog dialog) {
    final MemberInfo[] infos = ArrayUtil.toObjectArray(dialog.getSelectedMemberInfos(), MemberInfo.class);
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    final PsiPackage targetPackage;
    if (targetDirectory != null) {
      targetPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
    }
    else {
      targetPackage = null;
    }
    final MultiMap<PsiElement,String> conflicts = new MultiMap<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
      final PsiClass superClass =
        mySubclass.getExtendsListTypes().length > 0 || mySubclass instanceof PsiAnonymousClass ? mySubclass.getSuperClass() : null;
      conflicts.putAllValues(PullUpConflictsUtil.checkConflicts(infos, mySubclass, superClass, targetPackage, targetDirectory,
                                                                dialog.getContainmentVerifier(), false));
    }), RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) return false;
    ExtractSuperClassUtil.checkSuperAccessible(targetDirectory, conflicts, mySubclass);
    return ExtractSuperClassUtil.showConflicts(dialog, conflicts, myProject);
  }

  // invoked inside Command and Atomic action
  private void doRefactoring(final Project project, final PsiClass subclass, final ExtractSuperclassDialog dialog) {
    final String superclassName = dialog.getExtractedSuperName();
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    final MemberInfo[] selectedMemberInfos = ArrayUtil.toObjectArray(dialog.getSelectedMemberInfos(), MemberInfo.class);
    final DocCommentPolicy javaDocPolicy = new DocCommentPolicy(dialog.getDocCommentPolicy());
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName(subclass, superclassName));
    try {
      final PsiClass superclass;

      try {
        superclass =
          ExtractSuperClassUtil.extractSuperClass(project, targetDirectory, superclassName, subclass, selectedMemberInfos, javaDocPolicy);
      }
      finally {
        a.finish();
      }

      // ask whether to search references to subclass and turn them into refs to superclass if possible
      if (superclass != null) {
        final SmartPsiElementPointer<PsiClass> classPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(subclass);
        final SmartPsiElementPointer<PsiClass> interfacePointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(superclass);
        final Runnable turnRefsToSuperRunnable = () -> ExtractClassUtil.askAndTurnRefsToSuper(project, classPointer, interfacePointer);
        SwingUtilities.invokeLater(turnRefsToSuperRunnable);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

  }

  private String getCommandName(final PsiClass subclass, String newName) {
    return RefactoringBundle.message("extract.superclass.command.name", newName, DescriptiveNameUtil.getDescriptiveName(subclass));
  }

  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PsiClass && !((PsiClass) elements[0]).isInterface()
      &&!((PsiClass)elements[0]).isEnum();
  }
}
