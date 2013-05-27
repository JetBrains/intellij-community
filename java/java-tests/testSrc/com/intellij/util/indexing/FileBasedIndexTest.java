package com.intellij.util.indexing;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 *         Date: 5/23/13
 */
public class FileBasedIndexTest extends LightCodeInsightFixtureTestCase {

  public void testSurviveOnFileTypeChange() throws Exception {
    myFixture.configureByText("Foo.java", "class Foo { String bar; }");
    myFixture.testHighlighting();
    FileTypeIndexTest.addAndRemoveFileType();
    myFixture.configureByText("Bar.java", "class Bar { String bar; }");
    myFixture.testHighlighting();
  }
}
