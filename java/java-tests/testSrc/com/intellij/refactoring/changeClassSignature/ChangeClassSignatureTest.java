package com.intellij.refactoring.changeClassSignature;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.LightRefactoringTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import static com.intellij.ui.plaf.beg.BegResources.m;

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

  public void testNoParams() throws Exception {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.lang.String", "")
    });
  }

  public void testInstanceOf() throws Exception {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.lang.String", "")
    });
  }

  public void testSubstituteParamInsideClass() throws Exception {
    doTest(aClass -> new TypeParameterInfo[0]);
  }

  public void testRemoveAllParams() throws Exception {
    doTest(aClass -> new TypeParameterInfo[0]);
  }

  public void testReorderParams() throws Exception {
    doTest(aClass -> new TypeParameterInfo[] {
      new TypeParameterInfo.Existing(1),
      new TypeParameterInfo.Existing(0)
    });
  }

  public void testAddParam() throws Exception {
    doTest(aClass -> new TypeParameterInfo[] {
      new TypeParameterInfo.Existing(0),
      new TypeParameterInfo.New(aClass, "E", "L<T>", "")
    });
  }

  public void testAddParamDiamond() throws Exception {
    doTest(aClass -> new TypeParameterInfo[] {
      new TypeParameterInfo.Existing(0),
      new TypeParameterInfo.New(aClass, "I", "Integer", "")
    });
  }

  public void testAddOneFirst() throws Exception {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.lang.String", "")
    }, "Zero.java", "OneString.java");
  }

  public void testAddManyFirst() throws Exception {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "U", "SubjectFace", ""),
      new TypeParameterInfo.New(aClass, "V", "java.util.Set<java.lang.Object>", "")
    }, "Zero.java", "TwoSubjectFaceSetObject.java");
  }

  public void testRemoveOneLast() throws Exception {
    doTest(aClass -> new TypeParameterInfo[0], "OneString.java", "Zero.java");
  }

  public void testRemoveManyLast() throws Exception {
    doTest(aClass -> new TypeParameterInfo[0], "TwoSubjectFaceSetObject.java", "Zero.java");
  }

  public void testModifyWithBound() throws Exception {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.util.List", "java.util.Collection"),
      new TypeParameterInfo.Existing(0)
    });
  }

  public void testAddWithBound() throws Exception {
    doTest(aClass -> new TypeParameterInfo[]{
      new TypeParameterInfo.New(aClass, "T", "java.util.List", "java.util.Collection")
    });
  }

  public void testAddBoundWithIntersection() throws Exception {
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

  private void doTest(Function<PsiClass, TypeParameterInfo[]> gen) throws Exception {
    @NonNls final String filePathBefore = getTestName(false) + ".java";
    @NonNls final String filePathAfter = getTestName(false) + ".java.after";
    doTest(gen, filePathBefore, filePathAfter);
  }

  private void doTest(Function<PsiClass, TypeParameterInfo[]> paramsGenerator, @NonNls String filePathBefore, @NonNls String filePathAfter)
    throws Exception {
    final String filePath = DATA_PATH + filePathBefore;
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on class name", targetElement instanceof PsiClass);
    PsiClass aClass = (PsiClass)targetElement;
    new ChangeClassSignatureProcessor(getProject(), aClass, paramsGenerator.apply(aClass)).run();
    checkResultByFile(DATA_PATH + filePathAfter);
  }
}
