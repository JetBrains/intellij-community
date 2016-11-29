/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class JavaPushDownHandler implements RefactoringActionHandler, ElementsHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("push.members.down.title");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ArrayList<PsiElement> elements = new ArrayList<>();
    String errorMessage = null;
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      int offset = caret.getOffset();
      PsiElement element = file.findElementAt(offset);
      String errorFromElement = collectElementsUnderCaret(element, elements);
      if (errorFromElement != null) {
        errorMessage = errorFromElement;
      }
    }

    if (elements.isEmpty()) {
      String message = errorMessage != null ? errorMessage
                                            : RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.inside.a.class.to.push.members.from"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MEMBERS_PUSH_DOWN);
      return;
    }

    invoke(project, elements.toArray(PsiElement.EMPTY_ARRAY), dataContext);
  }

  private static String collectElementsUnderCaret(PsiElement element, List<PsiElement> elements) {
    while (true) {
      if (element == null || element instanceof PsiFile) {
        return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.inside.a.class.to.push.members.from"));
      }

      if (element instanceof PsiClass && ((PsiClass)element).getQualifiedName() != null || element instanceof PsiField || element instanceof PsiMethod) {
        if (element instanceof JspClass) {
          return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("refactoring.is.not.supported.for.jsp.classes"));
        }
        elements.add(element);
        return null;
      }
      element = element.getParent();
    }
  }

  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(elements), PsiClass.class, false);
    if (aClass == null) return;

    String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) return;

    final Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.message("refactoring.cannot.be.performed") +
                                                           ": Class " + aClass.getName() + " is final", REFACTORING_NAME, HelpID.MEMBERS_PUSH_DOWN);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(aClass, element -> !(element instanceof PsiEnumConstant));

    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(aClass);

    for (MemberInfoBase<PsiMember> member : members) {
      for (PsiElement element : elements) {
        if (PsiTreeUtil.isAncestor(member.getMember(), element, false)) {
          member.setChecked(true);
          break;
        }
      }
    }
    PushDownDialog dialog = new PushDownDialog(project, members.toArray(new MemberInfo[members.size()]), aClass);
    dialog.show();
  }

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