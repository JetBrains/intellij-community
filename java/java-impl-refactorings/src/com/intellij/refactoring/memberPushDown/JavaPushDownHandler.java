// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPushDown;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaPushDownHandler implements ElementsHandler, ContextAwareActionHandler {
  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    final List<PsiElement> elements =
      CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, file, PsiCodeBlock.class, e -> e instanceof PsiMember);
    if (elements.isEmpty()) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(elements.get(0), PsiClass.class, false);
    if (psiClass == null) return false;
    return ClassInheritorsSearch.search(psiClass).asIterable().iterator().hasNext();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

    List<PsiElement> elements = CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, file, null, e ->  e instanceof PsiMember);
    invoke(project, elements.toArray(PsiElement.EMPTY_ARRAY), dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    PsiElement parent = PsiTreeUtil.findCommonParent(elements);
    PsiClass aClass = parent instanceof PsiClass parentClass
                      ? parentClass
                      : PsiTreeUtil.getParentOfType(parent, PsiClass.class, false);
    final Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    if (aClass == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.inside.a.class.to.push.members.from"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MEMBERS_PUSH_DOWN);
      return;
    }
    if (aClass instanceof JspClass) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("refactoring.is.not.supported.for.jsp.classes"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MEMBERS_PUSH_DOWN);
      return;
    }
    if (aClass instanceof PsiAnonymousClass) {
      String message = JavaRefactoringBundle.message("class.is.anonymous.warning.message");
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MEMBERS_PUSH_DOWN);
      return;
    }
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      String message = JavaRefactoringBundle.message("class.is.final.warning.message");
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MEMBERS_PUSH_DOWN);
      return;
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

    final Set<PsiElement> selectedMembers = new HashSet<>();
    Collections.addAll(selectedMembers, elements);
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(aClass, element -> !(element instanceof PsiEnumConstant));
    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(aClass);
    for (MemberInfo memberInfo : members) {
      if (selectedMembers.contains(memberInfo.getMember())) {
        memberInfo.setChecked(true);
      }
    }
    PushDownDialog dialog = new PushDownDialog(project, members.toArray(new MemberInfo[0]), aClass);
    dialog.show();
  }

  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    return ContainerUtil.exists(elements, element -> element instanceof PsiMember);
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("push.members.down.title");
  }
}