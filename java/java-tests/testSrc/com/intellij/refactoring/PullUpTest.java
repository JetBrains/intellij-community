/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PullUpTest extends LightRefactoringTestCase {
  private static final String BASE_PATH = "/refactoring/pullUp/";

  public void testQualifiedThis() {
    doTest(new RefactoringTestUtil.MemberDescriptor("Inner", PsiClass.class));
  }

  public void testQualifiedSuper() {
    doTest(new RefactoringTestUtil.MemberDescriptor("Inner", PsiClass.class));
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

  public void testEscalateVisibility() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testExtensionMethod() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testPreserveOverride() {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  private void doTest(RefactoringTestUtil.MemberDescriptor... membersToFind) {
    doTest(true, membersToFind);
  }

  private void doTest(final boolean checkMembersMovedCount, RefactoringTestUtil.MemberDescriptor... membersToFind) {
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
    MemberInfo[] infos = RefactoringTestUtil.findMembers(sourceClass, membersToFind);

    final int[] countMoved = {0};
    final MoveMemberListener listener = new MoveMemberListener() {
      @Override
      public void memberMoved(PsiClass aClass, PsiMember member) {
        assertEquals(sourceClass, aClass);
        countMoved[0]++;
      }
    };
    JavaRefactoringListenerManager.getInstance(getProject()).addMoveMembersListener(listener);
    final PullUpProcessor helper = new PullUpProcessor(sourceClass, targetClass, infos, new DocCommentPolicy(DocCommentPolicy.ASIS));
    helper.run();
    UIUtil.dispatchAllInvocationEvents();
    JavaRefactoringListenerManager.getInstance(getProject()).removeMoveMembersListener(listener);
    if (checkMembersMovedCount) {
      assertEquals(countMoved[0], membersToFind.length);
    }
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
