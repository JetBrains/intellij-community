/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.extractInterface;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ExtractInterfaceHandler implements RefactoringActionHandler, ElementsHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(ExtractInterfaceHandler.class);

  private Project myProject;
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
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.EXTRACT_INTERFACE);
        return;
      }
      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  @Override
  public void invoke(@NotNull final Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    myProject = project;
    myClass = (PsiClass)elements[0];

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, myClass)) return;

    final ExtractInterfaceDialog dialog = new ExtractInterfaceDialog(myProject, myClass);
    if (!dialog.showAndGet() || !dialog.isExtractSuperclass()) {
      return;
    }

    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    ExtractSuperClassUtil.checkSuperAccessible(dialog.getTargetDirectory(), conflicts, myClass);
    if (!ExtractSuperClassUtil.showConflicts(dialog, conflicts, myProject)) return;

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
                                   DocCommentPolicy javaDocPolicy) throws IncorrectOperationException {
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
    return elements.length == 1 && elements[0] instanceof PsiClass;
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("extract.interface.title");
  }
}