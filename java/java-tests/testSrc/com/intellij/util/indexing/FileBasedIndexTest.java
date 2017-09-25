package com.intellij.util.indexing;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.text.CharArrayCharSequence;

/**
 * @author Dmitry Avdeev
 *         Date: 5/23/13
 */
public class FileBasedIndexTest extends LightCodeInsightFixtureTestCase {

  public void testSurviveOnFileTypeChange() {
    myFixture.configureByText("Foo.java", "class Foo { String bar; }");
    myFixture.testHighlighting();
    FileTypeIndexTest.addAndRemoveFileType();
    myFixture.configureByText("Bar.java", "class Bar { String bar; }");
    myFixture.testHighlighting();
  }

  public void testLargeFile() {
    char[] text = new char[FileUtilRt.LARGE_FOR_CONTENT_LOADING + 42];
    final String clazz = "class Foo { String bar; }";
    for (int i = 0; i < text.length; i++) {
      text[i] = i < clazz.length() ? clazz.charAt(i) : ' ';
    }
    final LightVirtualFile file = new LightVirtualFile("Foo.java", new CharArrayCharSequence(text));
    assertFalse(((FileBasedIndexImpl)FileBasedIndex.getInstance()).isIndexingCandidate(file, StubUpdatingIndex.INDEX_ID));
  }
}
