package com.siyeh.ipp.decls;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ChangeVariableTypeToRhsTypeIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "decls/change_variable_type_to_rhs_type", IntentionPowerPackBundle.message("change.variable.type.to.rhs.type.intention.name", "ss", "ArrayList<String>"));

  @Test
  public void testSimple() { ipp.get().doTest("Declare 'ss' with type 'ArrayList<String>'"); }

  @Test
  public void testSameType() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testAnonymous() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testComments() { ipp.get().doTest(); }
}
