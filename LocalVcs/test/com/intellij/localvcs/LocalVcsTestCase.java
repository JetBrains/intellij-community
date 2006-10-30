package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Before;

public abstract class LocalVcsTestCase extends TestCase {
  protected LocalVcs myVcs;

  @Before
  public void setUp() {
    myVcs = new LocalVcs();
  }

  protected void assertRevisionContent(String expectedContent,
                                       Entry actualEntry) {
    assertEquals(expectedContent, actualEntry.getContent());
  }

  protected void assertRevisionsContent(String[] expectedContents,
                                        Collection<Entry> actualEntries) {
    List<String> actualContents = new ArrayList<String>();
    for (Entry rev : actualEntries) {
      actualContents.add(rev.getContent());
    }
    assertEquals(expectedContents, actualContents.toArray(new Object[0]));
  }
}
