package com.intellij.localvcs.core.storage;

import com.intellij.localvcs.core.LocalVcsTestCase;
import org.junit.Test;

public class ByteContentTest extends LocalVcsTestCase {
  @Test
  public void testEquality() {
    ByteContent bc = new ByteContent("abc".getBytes());

    assertTrue(bc.equals(new ByteContent("abc".getBytes())));
    assertFalse(bc.equals(new ByteContent("123".getBytes())));

    Content c = new StoredContent(null, -1) {
      @Override
      public byte[] getBytes() {
        return "abc".getBytes();
      }
    };

    assertTrue(bc.equals(c));
  }
}
