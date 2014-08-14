/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 18.06.2002
 * Time: 12:45:30
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.memberPullUp;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JavaPullUpHandler implements RefactoringActionHandler, PullUpDialog.Callback, ElementsHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPullUp.JavaPullUpHandler");
  public static final String REFACTORING_NAME = RefactoringBundle.message("pull.members.up.title");
  private PsiClass mySubclass;
  private Project myProject;

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);

    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.inside.a.class.to.pull.members.from"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MEMBERS_PULL_UP);
        return;
      }

      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, element)) return;

      if (element instanceof PsiClass || element instanceof PsiField || element instanceof PsiMethod) {
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

    PsiElement element = elements[0];
    PsiClass aClass;
    PsiElement aMember = null;

    if (element instanceof PsiClass) {
      aClass = (PsiClass)element;
    }
    else if (element instanceof PsiMethod) {
      aClass = ((PsiMethod)element).getContainingClass();
      aMember = element;
    }
    else if (element instanceof PsiField) {
      aClass = ((PsiField)element).getContainingClass();
      aMember = element;
    }
    else {
      return;
    }

    invoke(project, dataContext, aClass, aMember);
  }

  private void invoke(Project project, DataContext dataContext, PsiClass aClass, PsiElement aMember) {
    final Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    if (aClass == null) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context", REFACTORING_NAME));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MEMBERS_PULL_UP);
      return;
    }

    ArrayList<PsiClass> bases = RefactoringHierarchyUtil.createBasesList(aClass, false, true);

    if (bases.isEmpty()) {
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) {
        invoke(project, dataContext, containingClass, aClass);
        return;
      }
      String message = RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("class.does.not.have.base.classes.interfaces.in.current.project", aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MEMBERS_PULL_UP);
      return;
    }


    mySubclass = aClass;
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(mySubclass, new MemberInfo.Filter<PsiMember>() {
      @Override
      public boolean includeMember(PsiMember element) {
        return true;
      }
    });
    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(mySubclass);
    PsiManager manager = mySubclass.getManager();

    for (MemberInfoBase<PsiMember> member : members) {
      if (manager.areElementsEquivalent(member.getMember(), aMember)) {
        member.setChecked(true);
        break;
      }
    }

    final PullUpDialog dialog = new PullUpDialog(project, aClass, bases, memberInfoStorage, this);


    dialog.show();
  }



  @Override
  public boolean checkConflicts(final PullUpDialog dialog) {
    final List<MemberInfo> infos = dialog.getSelectedMemberInfos();
    final MemberInfo[] memberInfos = infos.toArray(new MemberInfo[infos.size()]);
    final PsiClass superClass = dialog.getSuperClass();
    if (!checkWritable(superClass, memberInfos)) return false;
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            final PsiDirectory targetDirectory = superClass.getContainingFile().getContainingDirectory();
            final PsiPackage targetPackage = targetDirectory != null ? JavaDirectoryService.getInstance().getPackage(targetDirectory) : null;
            conflicts
              .putAllValues(PullUpConflictsUtil.checkConflicts(memberInfos, mySubclass, superClass, targetPackage, targetDirectory, dialog.getContainmentVerifier()));
          }
        });

      }
    }, RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) return false;
    if (!conflicts.isEmpty()) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      conflictsDialog.show();
      final boolean ok = conflictsDialog.isOK();
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
    /*
    if (elements.length == 1) {
      return elements[0] instanceof PsiClass || elements[0] instanceof PsiField || elements[0] instanceof PsiMethod;
    }
    else if (elements.length > 1){
      for (int  idx = 0;  idx < elements.length;  idx++) {
        PsiElement element = elements[idx];
        if (!(element instanceof PsiField || element instanceof PsiMethod)) return false;
      }
      return true;
    }
    return false;
    */
    // todo: multiple selection etc
    return elements.length == 1 && elements[0] instanceof PsiClass;
  }
}
