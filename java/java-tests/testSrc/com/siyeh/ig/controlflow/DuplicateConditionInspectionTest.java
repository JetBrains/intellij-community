package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DuplicateConditionInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  public void testDuplicateCondition() {
    doTest();
  }
  public void testDuplicateConditionNoSideEffect() {
    doTest();
  }
  public void testDuplicateBooleanBranch() {
    doTest();
  }
  public void testDuplicateWithNegation() {
    doTest();
  }
  public void testFix() {
    doTest();
    IntentionAction action = myFixture.findSingleIntention("Navigate to duplicate");
    myFixture.checkIntentionPreviewHtml(action, "<p>&rarr; <icon src=\"icon\"/>&nbsp;Fix.java, line #3</p>");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    DuplicateConditionInspection inspection = new DuplicateConditionInspection();
    inspection.ignoreSideEffectConditions = getTestName(false).contains("NoSideEffect");
    return inspection;
  }
}