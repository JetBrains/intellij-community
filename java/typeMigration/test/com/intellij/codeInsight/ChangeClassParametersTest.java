package com.intellij.codeInsight;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.refactoring.typeMigration.intentions.ChangeClassParametersIntention;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * User: anna
 */
public class ChangeClassParametersTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNestedTypeElements() {
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
    assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
  }
}
