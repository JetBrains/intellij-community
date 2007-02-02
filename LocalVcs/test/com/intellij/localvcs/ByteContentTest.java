package com.intellij.localvcs;

import org.junit.Test;

public class ByteContentTest extends LocalVcsTestCase {
  @Test
  public void testEquality() {
    ByteContent bc = new ByteContent("abc".getBytes());

    assertTrue(bc.equals(new ByteContent("abc".getBytes())));
    assertFalse(bc.equals(new ByteContent("123".getBytes())));

    Content c = new Content(null, -1) {
      @Override
      public byte[] getBytes() {
        return "abc".getBytes();
      }
    };

    assertTrue(bc.equals(c));
  }
}
