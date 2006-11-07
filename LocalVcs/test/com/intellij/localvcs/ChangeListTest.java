package com.intellij.localvcs;

import org.junit.Test;

public class ChangeListTest extends TestCase {
  @Test
  public void testDoesNotRegisterChangeSetOnApplyingError() {
    CreateFileChange badChange = new CreateFileChange(null, null) {
      @Override
      public void applyTo(Snapshot snapshot) {
        throw new SomeLocalVcsException();
      }
    };

    ChangeList l = new ChangeList();
    try {
      l.applyChangeSetOn(s, cs(badChange));
    } catch (SomeLocalVcsException e) {}

    assertTrue(l.getChangeSets().isEmpty());
  }

  private static class SomeLocalVcsException extends LocalVcsException {
  }
}
