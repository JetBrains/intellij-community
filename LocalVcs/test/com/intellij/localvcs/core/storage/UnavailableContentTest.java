package com.intellij.localvcs.core.storage;

import com.intellij.localvcs.core.LocalVcsTestCase;
import org.junit.Test;

public class UnavailableContentTest extends LocalVcsTestCase {
  @Test
  public void testContentAndLength() {
    assertEquals("content is not available", new String(new UnavailableContent().getBytes()));
    assertEquals(0, new UnavailableContent().getLength());
  }

  @Test
  public void testDoesNotEqualToAnyOtherContent() {
    final UnavailableContent u = new UnavailableContent();
    ByteContent b = new ByteContent(u.getBytes());

    assertFalse(u.equals(b));
    assertFalse(b.equals(u));

    assertFalse(u.equals(new Content(null, -1) {
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
