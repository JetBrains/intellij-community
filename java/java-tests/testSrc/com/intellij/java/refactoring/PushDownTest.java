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
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author anna
 * @since 13-Mar-2008
 */
public class PushDownTest extends LightRefactoringTestCase {
  private static final String BASE_PATH = "/refactoring/pushDown/";

  public void testTypeParameter() { doTest(); }
  public void testTypeParameterErasure() { doTest(); }
  public void testFieldTypeParameter() { doTest(); }
  public void testBodyTypeParameter() { doTest(); }
  public void testDisagreeTypeParameter() { doTest(true); }
  public void testFieldAndReferencedClass() { doTest(); }
  public void testFieldAndStaticReferencedClass() { doTest(); }
  public void testThisRefInAnonymous() { doTest(); }
  public void testSuperOverHierarchyConflict() { doTest(true); }
  public void testSuperOverHierarchy() { doTest(); }
  public void testMethodTypeParametersList() { doTest(); }
  public void testMethodFromInterfaceToAbstractClass() { doTest(); }
  public void testOverridingMethodWithSubst() { doTest(); }
  public void testSameClassInterface() { doTestImplements(); }
  public void testPreserveTypeArgs() { doTestImplements(); }
  public void testSubstTypeArgs() { doTestImplements(); }
  public void testExtensionMethodToInterface() { doTest(); }
  public void testExtensionMethodToClass() { doTest(); }

  public void testFunctionalExpression() { doTest(true);}
  public void testFunctionalInterface() { doTest(true);}
  public void testFunctionalExpressionDefaultMethod() { doTest();}

  public void testInterfaceConstants() { doTest();}

  public void testReferenceForMovedInnerClass() { doTest();}

  public void testDefaultMethodToInterface() {doTest();}
  public void testDefaultMethodToInterfaceKeepAbstract() {doTestImplements(true);}
  public void testDefaultMethodToClass() {doTest();}
  public void testDefaultMethodToClassKeepAbstract() { doTestImplements(true); }

  public void testInterfaceStaticMethodToInterface() { doTest(); }
  public void testInterfaceStaticMethodToClass() { doTest(); }

  public void testInterfaceMethodToClass() { doTest();}

  public void testInsertOverrideWhenKeepAbstract() {
    doTestImplements(true);
  }

  public void testErasureIfInheritsWithRawSubstitution() {
    doTest();
  }

  public void testAlreadyContainsMethodWithTheSignatureForGenericsSuperclass() {
    doTest(true);
  }

  public void testJavadocWhenKeepAsAbstractInterface() {
    doTestImplements(true);
  }

  public void testJavadocWhenKeepAsAbstractClass() {
    doTestImplements(true);
  }

  public void testPreserveOverrideAnnotationAfterConflict() {
    doTestImplements(true, true);
  }

  public void testInterfaceVisibilityInClass() {
    doTest();
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean failure) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on member name", targetElement instanceof PsiMember);

    final PsiMember psiMember = (PsiMember)targetElement;

    final PsiClass currentClass = psiMember.getContainingClass();

    assert currentClass != null;

    final List<MemberInfo> membersToMove = new ArrayList<>();

    final PsiField fieldByName = currentClass.findFieldByName("fieldToMove", false);
    if (fieldByName != null) {
      final MemberInfo memberInfo = new MemberInfo(fieldByName);
      memberInfo.setChecked(true);
      membersToMove.add(memberInfo);
    }

    final PsiClass classByName = currentClass.findInnerClassByName("ClassToMove", false);
    if (classByName != null) {
      final MemberInfo memberInfo = new MemberInfo(classByName);
      memberInfo.setChecked(true);
      membersToMove.add(memberInfo);
    }

    final MemberInfo memberInfo = new MemberInfo(psiMember);
    memberInfo.setChecked(true);
    membersToMove.add(memberInfo);

    new PushDownProcessor<MemberInfo, PsiMember, PsiClass>(currentClass, membersToMove,
                          new DocCommentPolicy(DocCommentPolicy.ASIS)) {
      @Override
      protected boolean showConflicts(@NotNull MultiMap<PsiElement, String> conflicts, UsageInfo[] usages) {
        if (failure == conflicts.isEmpty()) {
          fail(failure ? "Conflict was not detected" : "False conflict was detected");
        }
        return true;
      }
    }.run();

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private void doTestImplements() {
    doTestImplements(false);
  }

  private void doTestImplements(boolean toAbstract) {
    doTestImplements(toAbstract, false);
  }

  private void doTestImplements(boolean toAbstract, boolean failure) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    PsiClass currentClass = JavaPsiFacade.getInstance(getProject()).findClass("Test", GlobalSearchScope.projectScope(getProject()));
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(currentClass, element -> true);
    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(currentClass);
    for (MemberInfo member : members) {
      member.setChecked(true);
      if (toAbstract) {
        member.setToAbstract(toAbstract);
      }
    }

    new PushDownProcessor<MemberInfo, PsiMember, PsiClass>(currentClass, members,
                          new DocCommentPolicy(DocCommentPolicy.ASIS)) {
      @Override
      protected boolean showConflicts(@NotNull MultiMap<PsiElement, String> conflicts, UsageInfo[] usages) {
        if (failure == conflicts.isEmpty()) {
          fail(failure ? "Conflict was not detected" : "False conflict was detected");
        }
        return true;
      }
    }.run();

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
