package com.intellij.localvcs;

import java.util.List;

import org.junit.Test;

public class ChangeListTest extends TestCase {
  @Test
  public void testDoesNotRegisterChangeSetOnApplyingError() {
    CreateFileChange badChange = new CreateFileChange(null, null, null) {
      @Override
      public void applyTo(RootEntry root) {
        throw new SomeLocalVcsException();
      }
    };

    ChangeList c = new ChangeList();
    try {
      c.applyChangeSetOn(new RootEntry(), cs(badChange));
    } catch (SomeLocalVcsException e) {}

    assertTrue(c.getChangeSets().isEmpty());
  }

  //@Test
  public void testCalculatingDifferenceListForModifiedFiles() {
    RootEntry r = new RootEntry();
    ChangeList c = new ChangeList();

    r = c.applyChangeSetOn(r, cs(new CreateFileChange(p("file"), "a", 1)));
    r = c.applyChangeSetOn(r, cs(new ChangeFileContentChange(p("file"), "b")));

    DifferenceList result = c.getDifferenceListFor(r.getEntry(p("file")));

    assertEquals(2, result.getDifferenceSets().size());

    List<Difference> d1 = result.getDifferenceSets().get(0).getDifferences();
    List<Difference> d2 = result.getDifferenceSets().get(1).getDifferences();

    assertEquals(1, d1.size());
    assertTrue(d1.get(0).isModifed());

    assertTrue(d2.isEmpty());
  }

  @Test
  public void testDoesNotIncludeDifferenceForAnotherFiles() {
  }

  private static class SomeLocalVcsException extends LocalVcsException {
  }
}
