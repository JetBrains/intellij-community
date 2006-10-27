package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;

public abstract class LocalVcsTestCase extends Assert {
  protected LocalVcs myVcs;

  @Before
  public void setUp() {
    myVcs = new LocalVcs();
  }

  protected void assertRevisionContent(String expectedContent,
                                       Revision actualRevision) {
    assertEquals(expectedContent, actualRevision.getContent());
  }

  protected void assertRevisionsContent(String[] expectedContents,
                                        Collection<Revision> actualRevisions) {
    List<String> actualContents = new ArrayList<String>();
    for (Revision rev : actualRevisions) {
      actualContents.add(rev.getContent());
    }
    assertEquals(expectedContents, actualContents.toArray(new Object[0]));
  }

  @SuppressWarnings("unchecked")
  protected void assertElements(Object[] expected, Collection actual) {
    assertEquals(expected.length, actual.size());
    assertTrue(actual.containsAll(Arrays.asList(expected)));
  }
}
