// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author anna
 */
@TestApplication
public class PushDownTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private static final String BASE_PATH = "/refactoring/pushDown/";

  @Test
  public void testTypeParameter() { doTest(); }
  @Test
  public void testTypeParameterErasure() { doTest(); }
  @Test
  public void testFieldTypeParameter() { doTest(); }
  @Test
  public void testBodyTypeParameter() { doTest(); }
  @Test
  public void testDisagreeTypeParameter() { doTest(true); }
  @Test
  public void testInaccessibleInTheSameTopLevel() { doTest(true); }
  @Test
  public void testStaticMethodNoConflictCallingOnQualifier() { doTest(); }
  @Test
  public void testFieldAndReferencedClass() { doTest(); }
  @Test
  public void testSecondNormalizedField() { doTest(); }
  @Test
  public void testFieldAndStaticReferencedClass() { doTest(); }
  @Test
  public void testThisRefInAnonymous() { doTest(); }
  @Test
  public void testSuperOverHierarchyConflict() { doTest(true); }
  @Test
  public void testPrivateFieldUsedFromMovedAnonymous() { doTest(true); }
  @Test
  public void testSuperOverHierarchy() { doTest(); }
  @Test
  public void testMethodTypeParametersList() { doTest(); }
  @Test
  public void testMethodFromInterfaceToAbstractClass() { doTest(); }
  @Test
  public void testOverridingMethodWithSubst() { doTest(); }
  @Test
  public void testSameClassInterface() { doTestImplements(); }
  @Test
  public void testPreserveTypeArgs() { doTestImplements(); }
  @Test
  public void testInterfaceExtends() { doTestImplements(); }
  @Test
  public void testSubstTypeArgs() { doTestImplements(); }
  @Test
  public void testExtensionMethodToInterface() { doTest(); }
  @Test
  public void testExtensionMethodToClass() { doTest(); }

  @Test
  public void testFunctionalExpression() { doTest(true);}
  @Test
  public void testFunctionalInterface() { doTest(true);}
  @Test
  public void testFunctionalExpressionDefaultMethod() { doTest();}
  @Test
  public void testInlineSuperMethodCall() { BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest());}
  @Test
  public void testRenameTypeParametersToAvoidHiding() { doTest();}
  @Test
  public void testNoRenameTypeParametersToAvoidHidingForStatic() { doTest();}

  @Test
  public void testInterfaceConstants() { doTest();}

  @Test
  public void testReferenceForMovedInnerClass() { doTest();}

  @Test
  public void testDefaultMethodToInterface() {doTest();}
  @Test
  public void testDefaultMethodToInterfaceKeepAbstract() {doTestImplements(true);}
  @Test
  public void testDefaultMethodToClass() {doTest();}
  @Test
  public void testDefaultMethodToClassKeepAbstract() { doTestImplements(true); }
  @Test
  public void testInterfaceStaticMethodToInterface() { doTest(); }
  @Test
  public void testInterfaceStaticMethodToClass() { doTest(); }
  @Test
  public void testInterfaceInnerRecordToClass() { doTest(); }
  @Test
  public void testInterfaceInnerClassToClass() { doTest(); }
  @Test
  public void testThisSuperExpressions() {doTest();}
  @Test
  public void testMethodsInheritedFromSuper() {doTest();}
  @Test
  public void testMethodsInheritedFromSuper1() {doTest();}
  @Test
  public void testCopyAnnotationsFromSuper() {doTest();}
  @Test
  public void testKeepBodyFromInterfaceMethod() {doTest();}

  @Test
  public void testInterfaceMethodToClass() { doTest();}

  @Test
  public void testInsertOverrideWhenKeepAbstract() {
    doTestImplements(true);
  }

  @Test
  public void testErasureIfInheritsWithRawSubstitution() {
    doTest();
  }

  @Test
  public void testAlreadyContainsMethodWithTheSignatureForGenericsSuperclass() {
    doTest(true);
  }

  @Test
  public void testJavadocWhenKeepAsAbstractInterface() {
    doTestImplements(true);
  }

  @Test
  public void testJavadocWhenKeepAsAbstractClass() {
    doTestImplements(true);
  }

  @Test
  public void testPreserveOverrideAnnotationAfterConflict() {
    doTestImplements(true, true);
  }

  @Test
  public void testPassingImplementsToAnonymous() {
    doTestImplements(false, true);
  }

  @Test
  public void testInterfaceVisibilityInClass() {
    doTest();
  }

  @Test
  public void testClassShouldBeAbstractConflict() {
    doTest(conflicts -> assertThat(conflicts.values()).hasSameElementsAs(Collections.singletonList("Non abstract class <b><code>B</code></b> will miss implementation of method <b><code>foo()</code></b>")));
  }

  @Test
  public void testClassInheritsUnrelatedDefaultsConflict() {
    doTest(conflicts -> assertThat(conflicts.values()).hasSameElementsAs(Collections.singletonList("Class <b><code>B</code></b> will inherit unrelated defaults from interface <b><code>A</code></b> and interface <b><code>I</code></b>")));
  }

  @Test
  public void testStaticToLocal() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, myFixture.getTestRootDisposable());
    doTest(conflicts -> assertThat(conflicts.values()).hasSameElementsAs(Collections.singletonList("Static method <b><code>foo()</code></b> can't be pushed to non-static local class <b><code>FooExt</code></b>")));
  }

  @Test
  public void testStaticToLocalWithReferenceUpdate() {
    doTest(conflicts -> assertThat(new HashSet<>(conflicts.values())).hasSameElementsAs(ContainerUtil.newHashSet(
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");

    myFixture.runInCommand(() -> {
      final PsiElement targetElement = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertInstanceOf(PsiMember.class, targetElement, "<caret> is not on member name");

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

      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  private void doTestImplements() {
    doTestImplements(false);
  }

  private void doTestImplements(boolean toAbstract) {
    doTestImplements(toAbstract, false);
  }

  private void doTestImplements(boolean toAbstract, boolean failure) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");

    myFixture.runInCommand(() -> {
      PsiClass currentClass = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("Test", GlobalSearchScope.projectScope(myFixture.getProject()));
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

      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }
}
