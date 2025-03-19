// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public class PullUpTest extends LightRefactoringTestCase {
  private static final String BASE_PATH = "/refactoring/pullUp/";

  private static final String IGNORE_CONFLICTS = "IGNORE";

  public void testQualifiedThis() {
    doTest(new RefactoringTestUtil.MemberDescriptor("Inner", PsiClass.class));
  }

  public void testQualifiedSuper() {
    doTest(new RefactoringTestUtil.MemberDescriptor("Inner", PsiClass.class));
  }

  public void testPullUpEnumMethodAndMakeAbstract() {
    doTest(new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, true));
  }

  public void testNoAbstractModifiersOnInterfaceMethods() {
    doTest(new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, true));
  }

  public void testQualifiedReference() {     // IDEADEV-25008
    doTest(new RefactoringTestUtil.MemberDescriptor("x", PsiField.class),
           new RefactoringTestUtil.MemberDescriptor("getX", PsiMethod.class),
           new RefactoringTestUtil.MemberDescriptor("setX", PsiMethod.class));
  }

  public void testPullUpInheritedStaticClasses() {
    doTest(new RefactoringTestUtil.MemberDescriptor("C", PsiClass.class),
           new RefactoringTestUtil.MemberDescriptor("D", PsiClass.class));
  }

  public void testPullUpPrivateInnerClassWithPrivateConstructor() {
    doTest(new RefactoringTestUtil.MemberDescriptor("C", PsiClass.class));
  }

  public void testPullUpAndMakeAbstract() {
    doTest(new RefactoringTestUtil.MemberDescriptor("a", PsiMethod.class),
           new RefactoringTestUtil.MemberDescriptor("b", PsiMethod.class, true));
  }

  public void testTryCatchFieldInitializer() {
    doTest(new RefactoringTestUtil.MemberDescriptor("field", PsiField.class));
  }

  public void testIfFieldInitializationWithNonMovedField() {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testIfFieldMovedInitialization() {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testMultipleConstructorsFieldInitialization() {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testMultipleConstructorsFieldInitializationNoGood() {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testRemoveOverride() {
    setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest(new RefactoringTestUtil.MemberDescriptor("get", PsiMethod.class));
  }

  public void testWarningsWithPrettyDescriptions() {
    doTest(true,
           """
             Concrete anonymous class derived from <b><code>X</code></b> will inherit a new abstract method
             Concrete class body of enum constant <b><code>F.A</code></b> will inherit a new abstract method
             Concrete enum <b><code>E</code></b> will inherit a new abstract method
             Concrete enum <b><code>F</code></b> will inherit a new abstract method
             Concrete record <b><code>Z</code></b> will inherit a new abstract method""",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class, true));
  }

  public void testRecordAbstractMethodWarning() {
    setLanguageLevel(LanguageLevel.JDK_16);
    doTest(true, "Concrete record <b><code>R1</code></b> will inherit a new abstract method",
           new RefactoringTestUtil.MemberDescriptor("bar", PsiMethod.class, true));
  }

  public void testNotFunctionalAnymore() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(true, "Functional expression demands functional interface to have exact one method",
           new RefactoringTestUtil.MemberDescriptor("get", PsiMethod.class, true));
  }

  public void testPullToInterfaceAsDefault() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(true, "Method <b><code>mass()</code></b> uses field <b><code>SimplePlanet.mass</code></b>, which is not moved to the superclass",
           new RefactoringTestUtil.MemberDescriptor("mass", PsiMethod.class, false));
  }

  public void testStillFunctional() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(true, new RefactoringTestUtil.MemberDescriptor("get", PsiMethod.class, false));
  }

  public void testAsDefault() {
    final RefactoringTestUtil.MemberDescriptor descriptor = new RefactoringTestUtil.MemberDescriptor("get", PsiMethod.class);
    doTest(descriptor);
  }

  public void testTypeParamErasure() {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testTypeParamSubst() {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testTypeArgument() {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testGenericsInAbstractMethod() {
    doTest(new RefactoringTestUtil.MemberDescriptor("method", PsiMethod.class, true));
  }

  public void testReplaceDuplicatesInInheritors() {
    doTest(new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, false));
  }

  public void testGenericsInImplements() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("I", PsiClass.class));
  }

  public void testUpdateStaticRefs() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testUpdateStaticRefsForInterface() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testRemoveOverrideFromPulledMethod() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testPreserveOverrideInPulledMethod() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testMergeInterfaces() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("I", PsiClass.class));
  }

  public void testTypeParamsConflictingNames() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, true));
  }
  
  public void testReferencedStaticMethodFromOuterClass() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, false));
  }
  
  public void testReferencedStaticGenericClassFromOuterClass() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class),
           new RefactoringTestUtil.MemberDescriptor("D", PsiClass.class));
  }

  public void testConflictOnNewAbstractMethod() {
    doTest(false, "Concrete class <b><code>C</code></b> will inherit a new abstract method", new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testEscalateVisibility() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testExtensionMethod() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testPreserveOverride() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testSubstituteOverrideToMerge() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testAsDefaultMethodOverAbstract() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testOrderVarargsParameterLast() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("f2", PsiField.class), new RefactoringTestUtil.MemberDescriptor("f1", PsiField.class));
  }

  public void testOuterClassRefsNoConflictIfAsAbstract() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("bar", PsiMethod.class, true));
  }

  public void testSuperFieldReferenced() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("bar", PsiMethod.class, false));
  }

  public void testOuterClassRefs() {
    doTest(false,
           """
             Method <b><code>bar()</code></b> uses field <b><code>Outer.x</code></b>, which is not moved to the superclass
             Method <b><code>bar()</code></b> uses method <b><code>Outer.foo()</code></b>, which is not moved to the superclass""",
           new RefactoringTestUtil.MemberDescriptor("bar", PsiMethod.class));
  }

  public void testClassInitializer() {
    doTest();
  }

  public void testRenameConflictingTypeParameters() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, false));
  }

  public void testDefaultMethodAsAbstract() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, true));
  }

  public void testDefaultMethodAsDefault() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, false));
  }

  public void testPublicMethodFromPrivateClassConflict() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("HM", PsiClass.class), new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testSOEOnSelfInheritance() {
    doTest(false, IGNORE_CONFLICTS, new RefactoringTestUtil.MemberDescriptor("test", PsiMethod.class));
  }

  public void testPullUpAsAbstractInClass() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("test", PsiMethod.class, true));
  }

  public void testPullUpFromAnonymousToInterface() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, true));
  }
  
  public void testSuperExprQualifying() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, false));
  }

  private void doTest(RefactoringTestUtil.MemberDescriptor... membersToFind) {
    doTest(true, membersToFind);
  }

  private void doTest(final boolean checkMembersMovedCount, RefactoringTestUtil.MemberDescriptor... membersToFind) {
    doTest(checkMembersMovedCount, null, membersToFind);
  }

  private void doTest(final boolean checkMembersMovedCount,
                      String conflictMessage,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) {
    final MultiMap<PsiElement, String> conflictsMap = new MultiMap<>();
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    final PsiClass sourceClass = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    assertNotNull(sourceClass);

    PsiClass targetClass = sourceClass.getSuperClass();
    assertNotNull(targetClass);
    if (!targetClass.isWritable()) {
      final PsiClass[] interfaces = sourceClass.getInterfaces();
      assertEquals(1, interfaces.length);
      assertTrue(interfaces[0].isWritable());
      targetClass = interfaces[0];
    }
    final MemberInfo[] infos = membersToFind.length == 0
                               ? new MemberInfo[]{new MemberInfo(PsiTreeUtil.getParentOfType(elementAt, PsiMember.class))}
                               : RefactoringTestUtil.findMembers(sourceClass, membersToFind);

    final int[] countMoved = {0};
    final MoveMemberListener listener = (aClass, member) -> {
      assertEquals(sourceClass, aClass);
      countMoved[0]++;
    };
    JavaRefactoringListenerManager.getInstance(getProject()).addMoveMembersListener(listener);
    final PsiDirectory targetDirectory = targetClass.getContainingFile().getContainingDirectory();
    final PsiPackage targetPackage = targetDirectory != null ? JavaDirectoryService.getInstance().getPackage(targetDirectory) : null;
    conflictsMap.putAllValues(
      PullUpConflictsUtil
        .checkConflicts(infos, sourceClass, targetClass, targetPackage, targetDirectory,
                        psiMethod -> PullUpProcessor.checkedInterfacesContain(Arrays.asList(infos), psiMethod))
    );
    final PullUpProcessor helper = new PullUpProcessor(sourceClass, targetClass, infos, new DocCommentPolicy(DocCommentPolicy.ASIS));
    helper.run();
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    JavaRefactoringListenerManager.getInstance(getProject()).removeMoveMembersListener(listener);

    if (conflictMessage != null && conflictsMap.isEmpty()) {
      fail("Conflict was not detected");
    }

    if (conflictMessage == null && !conflictsMap.isEmpty()) {
      fail(conflictsMap.values().iterator().next());
    }

    if (conflictMessage != null && !IGNORE_CONFLICTS.equals(conflictMessage)) {
      String actualMessage = conflictsMap.values().stream().sorted().collect(Collectors.joining("\n"));
      assertEquals(conflictMessage, actualMessage);
      return;
    }

    if (checkMembersMovedCount) {
      assertEquals(membersToFind.length == 0 ? 1 : membersToFind.length, countMoved[0]);
    }
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
