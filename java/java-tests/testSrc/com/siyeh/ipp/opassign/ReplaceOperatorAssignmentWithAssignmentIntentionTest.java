package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see ReplaceOperatorAssignmentWithAssignmentIntention
 */
@TestApplication
public class ReplaceOperatorAssignmentWithAssignmentIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "opassign/assignment", CommonQuickFixBundle.message("fix.replace.x.with.y", "+=", "="));

  @Test
  public void testOperatorAssignment1() { ipp.get().doTest(); }
  @Test
  public void testDoubleOpAssign() { ipp.get().doTest(); }
  @Test
  public void testStringOpAssign() { ipp.get().doTest(); }
  @Test
  public void testByteOpAssign() { ipp.get().doTest(); }
  @Test
  public void testPrecedence() { ipp.get().doTest(); }
  @Test
  public void testPolyadicAssignment() { ipp.get().doTest(CommonQuickFixBundle.message("fix.replace.x.with.y", "*=", "=")); }
  @Test
  public void testConditionalAssignment() { ipp.get().doTest(CommonQuickFixBundle.message("fix.replace.x.with.y",  "*=", "=")); }
  @Test
  public void testIncomplete() { ipp.get().doTest(); }
  @Test
  public void testCastNecessary() { ipp.get().doTest(); }
  @Test
  public void testAssignmentAssign() { ipp.get().doTest(); }
  @Test
  public void testAdditionAssignmentAssign() { ipp.get().doTest(); }
  @Test
  public void testInstanceofAssign() { ipp.get().doTest(); }
}
