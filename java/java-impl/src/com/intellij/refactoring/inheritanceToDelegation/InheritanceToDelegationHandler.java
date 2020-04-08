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

package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class InheritanceToDelegationHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(InheritanceToDelegationHandler.class);

  private static final MemberInfo.Filter<PsiMember> MEMBER_INFO_FILTER = new MemberInfo.Filter<PsiMember>() {
    @Override
    public boolean includeMember(PsiMember element) {
      if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)element;
        return !method.hasModifierProperty(PsiModifier.STATIC)
               && !method.hasModifierProperty(PsiModifier.PRIVATE);
      }
      else if (element instanceof PsiClass && ((PsiClass)element).isInterface()) {
        return true;
      }
      return false;
    }
  };

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    return !PsiUtil.isModuleFile(file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INHERITANCE_TO_DELEGATION);
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
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    final PsiClass aClass = (PsiClass)elements[0];

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (aClass.isInterface()) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("class.is.interface", aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INHERITANCE_TO_DELEGATION);
      return;
    }

    if (aClass instanceof JspClass) {
      RefactoringMessageUtil.showNotSupportedForJspClassesError(project, editor, getRefactoringName(), HelpID.INHERITANCE_TO_DELEGATION);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

    PsiClass[] bases = aClass.getSupers();
    @NonNls final String javaLangObject = CommonClassNames.JAVA_LANG_OBJECT;

    if (bases.length == 0 || bases.length == 1 && javaLangObject.equals(bases[0].getQualifiedName())) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("class.does.not.have.base.classes.or.interfaces", aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INHERITANCE_TO_DELEGATION);
      return;
    }

    final HashMap<PsiClass, Collection<MemberInfo>> basesToMemberInfos = new LinkedHashMap<>();

    for (PsiClass base : bases) {
      if (javaLangObject.equals(base.getQualifiedName())) continue;
      basesToMemberInfos.put(base, createBaseClassMemberInfos(base));
    }


    final Set<PsiClass> baseClasses = basesToMemberInfos.keySet();
    new InheritanceToDelegationDialog(project, aClass,
                                      baseClasses.toArray(PsiClass.EMPTY_ARRAY), basesToMemberInfos).show();
  }

  private static List<MemberInfo> createBaseClassMemberInfos(PsiClass baseClass) {
    final PsiClass deepestBase = RefactoringHierarchyUtil.getDeepestNonObjectBase(baseClass);
    LOG.assertTrue(deepestBase != null);

    final MemberInfoStorage memberInfoStorage = new MemberInfoStorage(baseClass, MEMBER_INFO_FILTER);

    List<MemberInfo> memberInfoList = new ArrayList<>();
    memberInfoList.addAll(memberInfoStorage.getClassMemberInfos(deepestBase));
    memberInfoList.addAll(memberInfoStorage.getIntermediateMemberInfosList(deepestBase));
    return memberInfoList;
  }

  public static String getRefactoringName() {
    return JavaRefactoringBundle.message("replace.inheritance.with.delegation.title");
  }
}