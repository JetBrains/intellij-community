package com.intellij.localvcs;

import org.junit.Test;

public class LongContentTest extends LocalVcsTestCase {
  @Test
  public void testContentAndLength() {
    assertEquals("content is too long", new String(new LongContent().getBytes()));
    assertEquals(0, new LongContent().getLength());
  }

  @Test
  public void testDoesNotEqualToAnyOtherContent() {
    final LongContent lc = new LongContent();
    ByteContent bc = new ByteContent(lc.getBytes());

    assertFalse(lc.equals(bc));
    assertFalse(bc.equals(lc));

    assertFalse(lc.equals(new Content(null, -1) {
      @Override
      public byte[] getBytes() {
        return lc.getBytes();
      }
    }));
  }

  @Test
  public void testIsTooLong() {
    assertFalse(new Content(null, -1).isTooLong());
    assertTrue(new LongContent().isTooLong());
  }
}
