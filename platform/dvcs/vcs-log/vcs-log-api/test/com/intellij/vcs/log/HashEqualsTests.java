package com.intellij.vcs.log;

import com.intellij.vcs.log.Hash;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author erokhins
 */
public class HashEqualsTests {

  @Test
  public void testEqualsSelf() throws Exception {
    Hash hash1 = Hash.build("adf");
    Assert.assertTrue(hash1.equals(hash1));
  }

  @Test
  public void testEqualsNull() throws Exception {
    Hash hash1 = Hash.build("adf");
    Assert.assertFalse(hash1.equals(null));
  }

  @Test
  public void testEquals() throws Exception {
    Hash hash1 = Hash.build("adf");
    Hash hash2 = Hash.build("adf");
    Assert.assertTrue(hash1.equals(hash2));
  }

  @Test
  public void testEqualsNone() throws Exception {
    Hash hash1 = Hash.build("");
    Hash hash2 = Hash.build("");
    Assert.assertTrue(hash1.equals(hash2));
  }

}
