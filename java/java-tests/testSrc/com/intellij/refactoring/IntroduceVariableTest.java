package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.containers.MultiMap;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

/**
 * @author dsl
 */
public class IntroduceVariableTest extends LightCodeInsightTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimpleExpression() throws Exception {
    doTest(new MockIntroduceVariableHandler("i", false, false, true, "int"));
  }

  public void testInsideFor() throws Exception {
    doTest(new MockIntroduceVariableHandler("temp", false, false, true, "int"));
  }

  public void testReplaceAll() throws Exception {
    doTest(new MockIntroduceVariableHandler("s", true, true, true, "java.lang.String"));
  }

  public void testIDEADEV3678() throws Exception {
    doTest(new MockIntroduceVariableHandler("component", true, true, true, CommonClassNames.JAVA_LANG_OBJECT));
  }

  public void testIDEADEV13369() throws Exception {
    doTest(new MockIntroduceVariableHandler("ints", true, true, true, "int[]"));
  }

  public void testAnonymousClass() throws Exception {
    doTest(new MockIntroduceVariableHandler("temp", true, false, true, "int"));
  }

  public void testAnonymousClass1() throws Exception {
    doTest(new MockIntroduceVariableHandler("runnable", false, false, false, CommonClassNames.JAVA_LANG_RUNNABLE));
  }

  public void testAnonymousClass2() throws Exception {
    doTest(new MockIntroduceVariableHandler("j", true, false, false, "int"));
  }

  public void testParenthized() throws Exception {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, "int"));
  }

  public void testMethodCall() throws Exception {
    doTest(new MockIntroduceVariableHandler("temp", true, true, true, CommonClassNames.JAVA_LANG_OBJECT));
  }

  public void testMethodCallInSwitch() throws Exception {
    doTest(new MockIntroduceVariableHandler("i", true, true, true, "int"));
  }

  public void testParenthizedOccurence() throws Exception {
    doTest(new MockIntroduceVariableHandler("empty", true, true, true, "boolean"));
  }

  public void testParenthizedOccurence1() throws Exception {
    doTest(new MockIntroduceVariableHandler("s", true, true, true, "java.lang.String"));
  }

  public void testConflictingField() throws Exception {
    doTest(new MockIntroduceVariableHandler("name", true, false, true, "java.lang.String"));
  }

  public void testConflictingFieldInExpression() throws Exception {
    doTest(new MockIntroduceVariableHandler("name", false, false, true, "int"));
  }

  public void testStaticConflictingField() throws Exception {
    doTest(new MockIntroduceVariableHandler("name", false, false, true, "int"));
  }

  public void testNonConflictingField() throws Exception {
     doTest(new MockIntroduceVariableHandler("name", false, false, true, "int"));
  }

  public void testScr16910() throws Exception {
    doTest(new MockIntroduceVariableHandler("i", true, true, false, "int"));
  }

  public void testSCR18295() throws Exception {
    doTest(new MockIntroduceVariableHandler("it", true, false, false, "java.lang.String"));
  }

  public void testSCR18295a() throws Exception {
    doTest(new MockIntroduceVariableHandler("it", false, false, false, "java.lang.String"));
  }

  public void testSCR10412() throws Exception {
    doTest(new MockIntroduceVariableHandler("newVar", false, false, false, "java.lang.String[]"));
  }

  public void testSCR22718() throws Exception {
    doTest(new MockIntroduceVariableHandler("object", true, true, false, CommonClassNames.JAVA_LANG_OBJECT));
  }

  public void testSCR26075() throws Exception {
    doTest(new MockIntroduceVariableHandler("wrong", false, false, false, "java.lang.String") {
      @Override
      protected void assertValidationResult(boolean validationResult) {
        assertFalse(validationResult);
      }

      @Override
      protected boolean reportConflicts(MultiMap<PsiElement,String> conflicts, final Project project, IntroduceVariableSettings dialog) {
        assertEquals(2, conflicts.size());
        Collection<? extends String> conflictsMessages = conflicts.values();
        assertTrue(conflictsMessages.contains("Introducing variable may break code logic."));
        assertTrue(conflictsMessages.contains("Local variable <b><code>c</code></b> is modified in loop body."));
        return false;
      }
    });
  }

  public void testConflictingFieldInOuterClass() throws Exception {
    doTest(new MockIntroduceVariableHandler("text", true, true, false, "java.lang.String"));
  }

  public void testSkipSemicolon() throws Exception {
    doTest(new MockIntroduceVariableHandler("mi5", false, false, false, "int"));
  }

  public void testInsideIf() throws Exception {
    doTest(new MockIntroduceVariableHandler("s1", false, false, false, "java.lang.String"));
  }

  public void testInsideElse() throws Exception {
    doTest(new MockIntroduceVariableHandler("s1", false, false, false, "java.lang.String"));
  }

  public void testInsideWhile() throws Exception {
    doTest(new MockIntroduceVariableHandler("temp", false, false, false, "int"));
  }

  public void testSCR40281() throws Exception {
    doTest(new MockIntroduceVariableHandler("temp", false, false, false, "Set<? extends Map<?,String>.Entry<?,String>>"));
  }

  public void testWithIfBranches() throws Exception {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, "int"));
  }

  public void testInsideForLoop() throws Exception {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, "int"));
  }

  public void testDuplicateGenericExpressions() throws Exception {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, "Foo2<? extends Runnable>"));
  }

  public void testStaticImport() throws Exception {
    doTest(new MockIntroduceVariableHandler("i", true, true, false, "int"));
  }

  public void testThisQualifier() throws Exception {
    doTest(new MockIntroduceVariableHandler("count", true, true, false, "int"));
  }

  public void testSubLiteral() throws Exception {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, "java.lang.String"));
  }

  public void testSubLiteral1() throws Exception {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, "java.lang.String"));
  }

  public void testSubLiteralFromExpression() throws Exception {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, "java.lang.String"));
  }

  public void testSubExpressionFromIntellijidearulezzz() throws Exception {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, "java.lang.String"));
  }

  public void testSubPrimitiveLiteral() throws Exception {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, "boolean"));
  }

  public void testArrayFromVarargs() throws Exception {
    doTest(new MockIntroduceVariableHandler("strs", false, false, false, "java.lang.String[]"));
  }
  
  public void testArrayFromVarargs1() throws Exception {
    doTest(new MockIntroduceVariableHandler("strs", false, false, false, "java.lang.String[]"));
  }
  
  public void testEnumArrayFromVarargs() throws Exception {
    doTest(new MockIntroduceVariableHandler("strs", false, false, false, "E[]"));
  }

  public void testFromFinalFieldOnAssignment() throws Exception {
    doTest(new MockIntroduceVariableHandler("strs", false, false, false, "java.lang.String"));
  }

  public void testNoArrayFromVarargs() throws Exception {
    try {
      doTest(new MockIntroduceVariableHandler("strs", false, false, false, "java.lang.String[]"));
    }
    catch (Exception e) {
      assertEquals(e.getMessage(), "Error message:Cannot perform refactoring.\n" +
                                   "Selected block should represent an expression.");
      return;
    }
    fail("Should not be able to perform refactoring");
  }

   public void testNoArrayFromVarargs1() throws Exception {
    try {
      doTest(new MockIntroduceVariableHandler("strs", false, false, false, "java.lang.String[]"));
    }
    catch (Exception e) {
      assertEquals(e.getMessage(), "Error message:Cannot perform refactoring.\n" +
                                   "Selected block should represent an expression.");
      return;
    }
    fail("Should not be able to perform refactoring");
  }

  public void testNonExpression() throws Exception {
    doTest(new MockIntroduceVariableHandler("sum", true, true, false, "int"));
  }
  public void testTypeAnnotations() throws Exception {
    doTest(new MockIntroduceVariableHandler("y1", true, false, false, "@TA C"));
  }

  public void testReturnStatementWithoutSemicolon() throws Exception {
    doTest(new MockIntroduceVariableHandler("b", true, true, false, "java.lang.String"));
  }

  public void testAndAndSubexpression() throws Exception {
    doTest(new MockIntroduceVariableHandler("ab", true, true, false, "boolean"));
  }
  
  public void testSubexpressionWithSpacesInSelection() throws Exception {
    doTest(new MockIntroduceVariableHandler("ab", true, true, false, "boolean"));
  }

  public void testDuplicatesAnonymousClassCreationWithSimilarParameters () throws Exception {
    doTest(new MockIntroduceVariableHandler("foo1", true, true, false, "Foo"));
  }

  public void testDifferentForeachParameters () throws Exception {
    doTest(new MockIntroduceVariableHandler("tostr", true, true, false, "java.lang.String"));
  }

  public void testCollapsedToDiamond() throws Exception {
    doTest(new MockIntroduceVariableHandler("a", true, true, true, "java.util.ArrayList<java.lang.String>"));
  }

  public void testCantCollapsedToDiamond() throws Exception {
    doTest(new MockIntroduceVariableHandler("a", true, true, true, "Foo<java.lang.Number>"));
  }
  
  public void testPolyadic() throws Exception {
    doTest(new MockIntroduceVariableHandler("b1", true, true, true, "boolean"));
  }
  
  public void testAssignmentToUnresolvedReference() throws Exception {
    doTest(new MockIntroduceVariableHandler("collection", true, true, true, "java.util.List<? extends java.util.Collection<?>>"));
  }

  public void testSiblingInnerClassType() throws Exception {
    doTest(new MockIntroduceVariableHandler("vari", true, false, false, "A.B") {
      @Override
      public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                                   PsiExpression expr, PsiExpression[] occurrences,
                                                   TypeSelectorManagerImpl typeSelectorManager,
                                                   boolean declareFinalIfAll,
                                                   boolean anyAssignmentLHS,
                                                   InputValidator validator,
                                                   PsiElement anchor, final OccurrencesChooser.ReplaceChoice replaceChoice) {
        final PsiType type = typeSelectorManager.getDefaultType();
        Assert.assertTrue(type.getPresentableText(), type.getPresentableText().equals("B"));
        return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS,
                                 validator, anchor, replaceChoice);
      }
    });
  }

  public void testNonExpressionPriorityFailure() throws Exception {
    doTest(new MockIntroduceVariableHandler("sum", true, true, false, "int"){
      @Override
      protected void showErrorMessage(Project project, Editor editor, String message) {
        assertEquals("Cannot perform refactoring.\n" +
                     "Extracting selected expression would change the semantic of the whole expression.", message);
      }
    });
  }


  public void testIncorrectExpressionSelected() throws Exception {
    try {
      doTest(new MockIntroduceVariableHandler("toString", false, false, false, "java.lang.String"));
    }
    catch (Exception e) {
      assertEquals(e.getMessage(), "Error message:Cannot perform refactoring.\n" +
                                   "Selected block should represent an expression.");
      return;
    }
    fail("Should not be able to perform refactoring");
  }
  
  public void testMultiCatchSimple() throws Exception {
    doTest(new MockIntroduceVariableHandler("e", true, true, false, "java.lang.Exception", true));
  }

  public void testMultiCatchTyped() throws Exception {
    doTest(new MockIntroduceVariableHandler("b", true, true, false, "java.lang.Exception", true));
  }

  public void testBeforeVoidStatement() throws Exception {
    doTest(new MockIntroduceVariableHandler("c", false, false, false, CommonClassNames.JAVA_LANG_OBJECT));
  }

  private void doTest(IntroduceVariableBase testMe) throws Exception {
    @NonNls String baseName = "/refactoring/introduceVariable/" + getTestName(false);
    configureByFile(baseName + ".java");
    testMe.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(baseName + ".after.java");
  }
}
