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

  public void testOnClassLevelNoDuplicates() throws Exception {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelNoDuplicates.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelNoDuplicates.java");
  }

  public void testOnClassLevelDuplicates() throws Exception {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelDuplicates.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelDuplicates.java");
  }

  public void testOnClassLevelDuplicates1() throws Exception {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelDuplicates1.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelDuplicates1.java");
  }

  public void testOnClassLevelBinary() throws Exception {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelBinary.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelBinary.java");
  }
  //multiple error elements on class level corresponding to the extracted fragment ------------------
  public void testOnClassLevelNewExpression() throws Exception {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelNewExpression.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelNewExpression.java");
  }

  public void testOnClassLevelClassForName() throws Exception {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelClassForName.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelClassForName.java");
  }
  //-------------------------------------------------------------------------------------------------

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