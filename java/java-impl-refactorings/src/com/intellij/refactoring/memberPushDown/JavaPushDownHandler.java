// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPushDown;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
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
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JavaPushDownHandler implements RefactoringActionHandler, ElementsHandler, ContextAwareActionHandler {
  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    Ref<@NlsContexts.DialogMessage String> message = Ref.create();
    final List<PsiElement> elements = getElements(editor, file, message, true);
    if (elements.isEmpty() || !message.isNull()) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(elements.get(0), PsiClass.class, false);
    if (psiClass == null) return false;
    return ClassInheritorsSearch.search(psiClass).iterator().hasNext();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

    Ref<@NlsContexts.DialogMessage String> errorMessage = Ref.create();
    List<PsiElement> elements = getElements(editor, file, errorMessage, false);
    if (elements.isEmpty() || !errorMessage.isNull()) {
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

      if (element instanceof PsiClass || element instanceof PsiField || element instanceof PsiMethod) {
        PsiClass aClass = element instanceof PsiClass ? (PsiClass)element : ((PsiMember)element).getContainingClass();
        if (aClass == null || aClass.getQualifiedName() == null) {
          return null;
        }
        if (aClass instanceof JspClass) {
          return RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("refactoring.is.not.supported.for.jsp.classes"));
        }
        if (aClass instanceof PsiAnonymousClass) {
          return JavaRefactoringBundle.message("class.is.anonymous.warning.message",
                                               RefactoringUIUtil.getDescription(aClass, false));
        }
        if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
          return JavaRefactoringBundle.message("class.is.final.warning.message",
                                               RefactoringUIUtil.getDescription(aClass, false));
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
    if (aClass == null || !CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

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