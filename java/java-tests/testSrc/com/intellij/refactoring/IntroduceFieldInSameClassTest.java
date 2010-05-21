package com.intellij.refactoring;

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.JavaTestUtil;

/**
 * @author ven
 */
public class IntroduceFieldInSameClassTest extends LightCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInClassInitializer () throws Exception {
    configureByFile("/refactoring/introduceField/before1.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, true);
    checkResultByFile("/refactoring/introduceField/after1.java");
  }

  public void testInElseClause() throws Exception {
    configureByFile("/refactoring/introduceField/beforeElseClause.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, true);
    checkResultByFile("/refactoring/introduceField/afterElseClause.java");
  }

  public void testOuterClass() throws Exception {
    configureByFile("/refactoring/introduceField/beforeOuterClass.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR, false);
    checkResultByFile("/refactoring/introduceField/afterOuterClass.java");
  }

  private static void performRefactoring(final BaseExpressionToFieldHandler.InitializationPlace initializationPlace, final boolean declareStatic) {
    new MockIntroduceFieldHandler(initializationPlace, declareStatic).invoke(getProject(), myEditor, myFile, null);
  }

  public void testForcedFieldType() throws Exception {
    configureByFile("/refactoring/introduceField/beforeForcedFieldType.java");
    new MockIntroduceFieldHandler(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, false){
      @Override
      protected PsiType getFieldType(PsiType type) {
        return PsiPrimitiveType.INT;
      }
    }.invoke(getProject(), myEditor, myFile, null);
    checkResultByFile("/refactoring/introduceField/afterForcedFieldType.java");
  }
}