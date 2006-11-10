package com.intellij.localvcs;

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

  private static class SomeLocalVcsException extends LocalVcsException {
  }
}
