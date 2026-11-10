package com.intellij.codeInsight;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.refactoring.typeMigration.intentions.ChangeClassParametersIntention;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * User: anna
 */
@TestApplication
public class ChangeClassParametersTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNestedTypeElements() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String text = """
      interface Fun<A, B> {}
      class Test {
        {
           new Fun<java.util.List<Int<caret>eger>, String> () {};
        }
      }""";

    myFixture.configureByText("a.java", text);
    myFixture.doHighlighting();
    myFixture.launchAction(new ChangeClassParametersIntention());
    assertNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
  }
}
