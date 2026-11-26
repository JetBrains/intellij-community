// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.changeClassSignature;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCodeFragment;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureProcessor;
import com.intellij.refactoring.changeClassSignature.Existing;
import com.intellij.refactoring.changeClassSignature.New;
import com.intellij.refactoring.changeClassSignature.TypeParameterInfo;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class ChangeClassSignatureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private static final @NonNls String DATA_PATH = "/refactoring/changeClassSignature/";

  @Test
  public void testNoParams() {
    doTest(aClass -> new TypeParameterInfo[]{
      new New(aClass, "T", "java.lang.String", "")
    });
  }

  @Test
  public void testInstanceOf() {
    doTest(aClass -> new TypeParameterInfo[]{
      new New(aClass, "T", "java.lang.String", "")
    });
  }

  @Test
  public void testSubstituteParamInsideClass() {
    doTest(_ -> new TypeParameterInfo[0]);
  }

  @Test
  public void testRemoveAllParams() {
    doTest(_ -> new TypeParameterInfo[0]);
  }

  @Test
  public void testReorderParams() {
    doTest(_ -> new TypeParameterInfo[]{
      new Existing(1),
      new Existing(0)
    });
  }

  @Test
  public void testAddParam() {
    doTest(aClass -> new TypeParameterInfo[]{
      new Existing(0),
      new New(aClass, "E", "L<T>", "")
    });
  }

  @Test
  public void testAddParamDiamond() {
    doTest(aClass -> new TypeParameterInfo[]{
      new Existing(0),
      new New(aClass, "I", "Integer", "")
    });
  }

  @Test
  public void testAddOneFirst() {
    doTest(aClass -> new TypeParameterInfo[]{
      new New(aClass, "T", "java.lang.String", "")
    }, "Zero.java", "OneString.java");
  }

  @Test
  public void testAddManyFirst() {
    doTest(aClass -> new TypeParameterInfo[]{
      new New(aClass, "U", "SubjectFace", ""),
      new New(aClass, "V", "java.util.Set<java.lang.Object>", "")
    }, "Zero.java", "TwoSubjectFaceSetObject.java");
  }

  @Test
  public void testRemoveOneLast() {
    doTest(_ -> new TypeParameterInfo[0], "OneString.java", "Zero.java");
  }

  @Test
  public void testRemoveManyLast() {
    doTest(_ -> new TypeParameterInfo[0], "TwoSubjectFaceSetObject.java", "Zero.java");
  }

  @Test
  public void testModifyWithBound() {
    doTest(aClass -> new TypeParameterInfo[]{
      new New(aClass, "T", "java.util.List", "java.util.Collection"),
      new Existing(0)
    });
  }

  @Test
  public void testAddWithBound() {
    doTest(aClass -> new TypeParameterInfo[]{
      new New(aClass, "T", "java.util.List", "java.util.Collection")
    });
  }

  @Test
  public void testAddBoundWithIntersection() throws PsiTypeCodeFragment.IncorrectTypeException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiTypeCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
      .createTypeCodeFragment("java.lang.Runnable & java.io.Serializable", myFixture.getFile(), true, JavaCodeFragmentFactory.ALLOW_INTERSECTION);
    PsiType bound = ReadAction.computeBlocking(() -> fragment.getType());
    doTest(aClass -> {
      return new TypeParameterInfo[]{
        new New("T", JavaPsiFacade.getElementFactory(aClass.getProject()).createTypeFromText("Some", aClass), bound)
      };
    });
  }

  private void doTest(Function<PsiClass, TypeParameterInfo[]> gen) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final @NonNls String filePathBefore = myFixture.getTestName(false) + ".java";
    final @NonNls String filePathAfter = myFixture.getTestName(false) + ".java.after";
    doTest(gen, filePathBefore, filePathAfter);
  }

  private void doTest(Function<PsiClass, TypeParameterInfo[]> paramsGenerator,
                      @NonNls String filePathBefore,
                      @NonNls String filePathAfter) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String filePath = DATA_PATH + filePathBefore;
    myFixture.configureByFile(filePath);
    myFixture.runInCommand(() -> {
      final PsiElement targetElement = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertInstanceOf(PsiClass.class, targetElement, "<caret> is not on class name");
      PsiClass aClass = (PsiClass)targetElement;
      new ChangeClassSignatureProcessor(myFixture.getProject(), aClass, paramsGenerator.apply(aClass)).run();
      myFixture.checkResultByFile(DATA_PATH + filePathAfter);
    });
  }
}
