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
package com.intellij.refactoring.memberPushDown;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
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
public class JavaPushDownHandler implements RefactoringActionHandler, ElementsHandler, ContextAwareActionHandler {
  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    final List<PsiElement> elements = getElements(editor, file, Ref.create(), true);
    if (elements.isEmpty()) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(elements.get(0), PsiClass.class, false);
    if (psiClass == null) return false;
    return ClassInheritorsSearch.search(psiClass).iterator().hasNext();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

    Ref<@NlsContexts.DialogMessage String> errorMessage = Ref.create();
    List<PsiElement> elements = getElements(editor, file, errorMessage, false);
    if (elements.isEmpty()) {
      String message =
        !errorMessage.isNull() ? errorMessage.get() : RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.inside.a.class.to.push.members.from"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MEMBERS_PUSH_DOWN);
    }
    else {
      invoke(project, elements.toArray(PsiElement.EMPTY_ARRAY), dataContext);
    }
  }

  @NotNull
  private static List<PsiElement> getElements(Editor editor, PsiFile file, Ref<@NlsContexts.DialogMessage String> errorMessage, boolean stopAtCodeBlock) {
    List<PsiElement> elements = new ArrayList<>();
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      int offset = caret.getOffset();
      PsiElement element = file.findElementAt(offset);
      String errorFromElement = collectElementsUnderCaret(element, elements, stopAtCodeBlock);
      if (errorFromElement != null) {
        errorMessage.set(errorFromElement);
      }
    }
    return elements;
  }

  private static @NlsContexts.DialogMessage String collectElementsUnderCaret(PsiElement element, List<? super PsiElement> elements, boolean stopAtCodeBlock) {
    while (true) {
      if (element == null || element instanceof PsiFile) {
        return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.inside.a.class.to.push.members.from"));
      }
      if (stopAtCodeBlock && element instanceof PsiCodeBlock) {
        return null;
      }

      if (element instanceof PsiClass && ((PsiClass)element).getQualifiedName() != null || element instanceof PsiField || element instanceof PsiMethod) {
        if (element instanceof JspClass) {
          return RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("refactoring.is.not.supported.for.jsp.classes"));
        }
        elements.add(element);
        return null;
      }
      element = element.getParent();
    }
  }

  @Override
  public void invoke(@NotNull final Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(elements), PsiClass.class, false);
    if (aClass == null) return;

    String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) return;

    final Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          RefactoringBundle.message("refactoring.cannot.be.performed") +
                                          JavaRefactoringBundle.message("class.is.final.warning.message", aClass.getName()),
                                          getRefactoringName(), HelpID.MEMBERS_PUSH_DOWN);
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
    PushDownDialog dialog = new PushDownDialog(project, members.toArray(new MemberInfo[0]), aClass);
    dialog.show();
  }

  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    // todo: multiple selection etc
    return elements.length == 1 && elements[0] instanceof PsiClass;
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("push.members.down.title");
  }
}