// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author anna
 */
public class PushDownTest extends LightRefactoringTestCase {
  private static final String BASE_PATH = "/refactoring/pushDown/";

  public void testTypeParameter() { doTest(); }
  public void testTypeParameterErasure() { doTest(); }
  public void testFieldTypeParameter() { doTest(); }
  public void testBodyTypeParameter() { doTest(); }
  public void testDisagreeTypeParameter() { doTest(true); }
  public void testInaccessibleInTheSameTopLevel() { doTest(true); }
  public void testStaticMethodNoConflictCallingOnQualifier() { doTest(); }
  public void testFieldAndReferencedClass() { doTest(); }
  public void testSecondNormalizedField() { doTest(); }
  public void testFieldAndStaticReferencedClass() { doTest(); }
  public void testThisRefInAnonymous() { doTest(); }
  public void testSuperOverHierarchyConflict() { doTest(true); }
  public void testPrivateFieldUsedFromMovedAnonymous() { doTest(true); }
  public void testSuperOverHierarchy() { doTest(); }
  public void testMethodTypeParametersList() { doTest(); }
  public void testMethodFromInterfaceToAbstractClass() { doTest(); }
  public void testOverridingMethodWithSubst() { doTest(); }
  public void testSameClassInterface() { doTestImplements(); }
  public void testPreserveTypeArgs() { doTestImplements(); }
  public void testInterfaceExtends() { doTestImplements(); }
  public void testSubstTypeArgs() { doTestImplements(); }
  public void testExtensionMethodToInterface() { doTest(); }
  public void testExtensionMethodToClass() { doTest(); }

  public void testFunctionalExpression() { doTest(true);}
  public void testFunctionalInterface() { doTest(true);}
  public void testFunctionalExpressionDefaultMethod() { doTest();}
  public void testInlineSuperMethodCall() { BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest());}
  public void testRenameTypeParametersToAvoidHiding() { doTest();}
  public void testNoRenameTypeParametersToAvoidHidingForStatic() { doTest();}

  public void testInterfaceConstants() { doTest();}

  public void testReferenceForMovedInnerClass() { doTest();}

  public void testDefaultMethodToInterface() {doTest();}
  public void testDefaultMethodToInterfaceKeepAbstract() {doTestImplements(true);}
  public void testDefaultMethodToClass() {doTest();}
  public void testDefaultMethodToClassKeepAbstract() { doTestImplements(true); }
  public void testInterfaceStaticMethodToInterface() { doTest(); }
  public void testInterfaceStaticMethodToClass() { doTest(); }
  public void testInterfaceInnerRecordToClass() { doTest(); }
  public void testInterfaceInnerClassToClass() { doTest(); }
  public void testThisSuperExpressions() {doTest();}
  public void testMethodsInheritedFromSuper() {doTest();}
  public void testMethodsInheritedFromSuper1() {doTest();}
  public void testCopyAnnotationsFromSuper() {doTest();}
  public void testKeepBodyFromInterfaceMethod() {doTest();}

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

  public void testPassingImplementsToAnonymous() {
    doTestImplements(false, true);
  }

  public void testInterfaceVisibilityInClass() {
    doTest();
  }

  public void testClassShouldBeAbstractConflict() {
    doTest(conflicts -> assertSameElements(conflicts.values(), Collections.singletonList("Non abstract class <b><code>B</code></b> will miss implementation of method <b><code>foo()</code></b>")));
  }

  public void testClassInheritsUnrelatedDefaultsConflict() {
    doTest(conflicts -> assertSameElements(conflicts.values(), Collections.singletonList("Class <b><code>B</code></b> will inherit unrelated defaults from interface <b><code>A</code></b> and interface <b><code>I</code></b>")));
  }

  public void testStaticToLocal() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(conflicts -> assertSameElements(conflicts.values(),
                                           Collections.singletonList("Static method <b><code>foo()</code></b> can't be pushed to non-static local class <b><code>FooExt</code></b>")));
  }

  public void testStaticToLocalWithReferenceUpdate() {
    doTest(conflicts -> assertSameElements(new HashSet<>(conflicts.values()),
                                           ContainerUtil.newHashSet(
                                             "Method <b><code>m()</code></b> uses method <b><code>foo()</code></b>, which is pushed down",
                                             "Method <b><code>m()</code></b> uses method <b><code>foo()</code></b>, which is pushed down")));
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean failure) {
    doTest(conflicts -> {
      if (failure == conflicts.isEmpty()) {
        fail(failure ? "Conflict was not detected" : "False conflict was detected");
      }
    });
  }

  private void doTest(final Consumer<? super MultiMap<PsiElement, String>> checkConflicts) {
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

    new PushDownProcessor<>(currentClass, membersToMove,
                            new DocCommentPolicy(DocCommentPolicy.ASIS)) {
      @Override
      protected boolean showConflicts(@NotNull MultiMap<PsiElement, String> conflicts, UsageInfo[] usages) {
        checkConflicts.accept(conflicts);
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
        member.setToAbstract(true);
      }
    }

    new PushDownProcessor<>(currentClass, members,
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
