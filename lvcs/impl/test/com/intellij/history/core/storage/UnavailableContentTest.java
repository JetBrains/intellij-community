package com.intellij.history.core.storage;

import com.intellij.history.core.LocalVcsTestCase;
import org.junit.Test;

public class UnavailableContentTest extends LocalVcsTestCase {
  @Test(expected = RuntimeException.class)
  public void testContentThrowsException() {
    new UnavailableContent().getBytes();
  }

  @Test
  public void testDoesNotEqualToAnyOtherContent() {
    final UnavailableContent u = new UnavailableContent();
    ByteContent b = new ByteContent(new byte[]{1, 2, 3});

    assertFalse(u.equals(b));
    assertFalse(b.equals(u));

    assertFalse(u.equals(new StoredContent(null, -1) {
      @Override
      public byte[] getBytes() {
        return u.getBytes();
      }
    }));
  }

  @Test
  public void testAvailability() {
    assertFalse(new UnavailableContent().isAvailable());
  }
}
