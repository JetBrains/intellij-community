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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.08.2002
 * Time: 17:17:27
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
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

public class InheritanceToDelegationHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationHandler");
  public static final String REFACTORING_NAME = RefactoringBundle.message("replace.inheritance.with.delegation.title");

  private static final MemberInfo.Filter<PsiMember> MEMBER_INFO_FILTER = new MemberInfo.Filter<PsiMember>() {
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


  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INHERITANCE_TO_DELEGATION);
        return;
      }

      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    final PsiClass aClass = (PsiClass)elements[0];

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (aClass.isInterface()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("class.is.interface", aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INHERITANCE_TO_DELEGATION);
      return;
    }

    if (aClass instanceof JspClass) {
      RefactoringMessageUtil.showNotSupportedForJspClassesError(project, editor, REFACTORING_NAME, HelpID.INHERITANCE_TO_DELEGATION);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

    PsiClass[] bases = aClass.getSupers();
    @NonNls final String javaLangObject = CommonClassNames.JAVA_LANG_OBJECT;

    if (bases.length == 0 || bases.length == 1 && javaLangObject.equals(bases[0].getQualifiedName())) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("class.does.not.have.base.classes.or.interfaces", aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INHERITANCE_TO_DELEGATION);
      return;
    }

    final HashMap<PsiClass, Collection<MemberInfo>> basesToMemberInfos = new LinkedHashMap<>();

    for (PsiClass base : bases) {
      if (javaLangObject.equals(base.getQualifiedName())) continue;
      basesToMemberInfos.put(base, createBaseClassMemberInfos(base));
    }


    final Set<PsiClass> baseClasses = basesToMemberInfos.keySet();
    new InheritanceToDelegationDialog(project, aClass,
                                      baseClasses.toArray(new PsiClass[baseClasses.size()]), basesToMemberInfos).show();
  }

  private static List<MemberInfo> createBaseClassMemberInfos(PsiClass baseClass) {
    final PsiClass deepestBase = RefactoringHierarchyUtil.getDeepestNonObjectBase(baseClass);
    LOG.assertTrue(deepestBase != null);

    final MemberInfoStorage memberInfoStorage = new MemberInfoStorage(baseClass, MEMBER_INFO_FILTER);

    ArrayList<MemberInfo> memberInfoList = new ArrayList<>(memberInfoStorage.getClassMemberInfos(deepestBase));
    List<MemberInfo> memberInfos = memberInfoStorage.getIntermediateMemberInfosList(deepestBase);
    for (final MemberInfo memberInfo : memberInfos) {
      memberInfoList.add(memberInfo);
    }

    return memberInfoList;
  }
}
