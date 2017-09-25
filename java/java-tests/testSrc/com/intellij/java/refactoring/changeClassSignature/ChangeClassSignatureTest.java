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
package com.intellij.java.refactoring.changeClassSignature;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.psi.*;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureProcessor;
import com.intellij.refactoring.changeClassSignature.TypeParameterInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author dsl
 */
public class ChangeClassSignatureTest extends LightRefactoringTestCase {
  @NonNls private static final String DATA_PATH = "/refactoring/changeClassSignature/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testNoParams() {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.lang.String", "")
    });
  }

  public void testInstanceOf() {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.lang.String", "")
    });
  }

  public void testSubstituteParamInsideClass() {
    doTest(aClass -> new TypeParameterInfo[0]);
  }

  public void testRemoveAllParams() {
    doTest(aClass -> new TypeParameterInfo[0]);
  }

  public void testReorderParams() {
    doTest(aClass -> new TypeParameterInfo[] {
      new TypeParameterInfo.Existing(1),
      new TypeParameterInfo.Existing(0)
    });
  }

  public void testAddParam() {
    doTest(aClass -> new TypeParameterInfo[] {
      new TypeParameterInfo.Existing(0),
      new TypeParameterInfo.New(aClass, "E", "L<T>", "")
    });
  }

  public void testAddParamDiamond() {
    doTest(aClass -> new TypeParameterInfo[] {
      new TypeParameterInfo.Existing(0),
      new TypeParameterInfo.New(aClass, "I", "Integer", "")
    });
  }

  public void testAddOneFirst() {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.lang.String", "")
    }, "Zero.java", "OneString.java");
  }

  public void testAddManyFirst() {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "U", "SubjectFace", ""),
      new TypeParameterInfo.New(aClass, "V", "java.util.Set<java.lang.Object>", "")
    }, "Zero.java", "TwoSubjectFaceSetObject.java");
  }

  public void testRemoveOneLast() {
    doTest(aClass -> new TypeParameterInfo[0], "OneString.java", "Zero.java");
  }

  public void testRemoveManyLast() {
    doTest(aClass -> new TypeParameterInfo[0], "TwoSubjectFaceSetObject.java", "Zero.java");
  }

  public void testModifyWithBound() {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.util.List", "java.util.Collection"),
      new TypeParameterInfo.Existing(0)
    });
  }

  public void testAddWithBound() {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.util.List", "java.util.Collection")
    });
  }

  public void testAddBoundWithIntersection() {
    doTest(aClass -> {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());
      final PsiFile context = aClass.getContainingFile();
      return new TypeParameterInfo[]{
        new TypeParameterInfo.New("T",
                                  factory.createTypeFromText("Some", context),
                                  PsiIntersectionType.createIntersection(factory.createTypeFromText("java.lang.Runnable", context),
                                                                         factory.createTypeFromText("java.io.Serializable", context)))
      };
    });
  }

  private void doTest(Function<PsiClass, TypeParameterInfo[]> gen) {
    @NonNls final String filePathBefore = getTestName(false) + ".java";
    @NonNls final String filePathAfter = getTestName(false) + ".java.after";
    doTest(gen, filePathBefore, filePathAfter);
  }

  private void doTest(Function<PsiClass, TypeParameterInfo[]> paramsGenerator, @NonNls String filePathBefore, @NonNls String filePathAfter) {
    final String filePath = DATA_PATH + filePathBefore;
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on class name", targetElement instanceof PsiClass);
    PsiClass aClass = (PsiClass)targetElement;
    new ChangeClassSignatureProcessor(getProject(), aClass, paramsGenerator.apply(aClass)).run();
    checkResultByFile(DATA_PATH + filePathAfter);
  }
}
