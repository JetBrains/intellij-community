package com.siyeh.ipp.whileloop;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see com.siyeh.ipp.whileloop.ReplaceDoWhileLoopWithWhileLoopIntention
 */
@TestApplication
public class ReplaceDoWhileWithWhileLoopIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "whileloop/replace_do_while_with_while_loop", IntentionPowerPackBundle.message("replace.do.while.loop.with.while.loop.intention.name"));

  @Test
  public void testWithoutBraces() { ipp.get().doTest(); }
  @Test
  public void testFinalVariable1() { ipp.get().doTest(); }
  @Test
  public void testFinalVariable2() { ipp.get().doTest(); }
  @Test
  public void testInfiniteLoop() { ipp.get().doTest(); }
}