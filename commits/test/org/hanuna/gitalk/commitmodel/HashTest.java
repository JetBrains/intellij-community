package org.hanuna.gitalk.commitmodel;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author erokhins
 */
public class HashTest {
    public void runStringTest(String strHash) {
        Hash hash = Hash.buildHash(strHash);
        Assert.assertEquals(strHash, hash.toStrHash());
    }

    @Test
    public void testBuildHash() throws Exception {
        runStringTest("0000f");
        runStringTest("ff01a");
        runStringTest("0000");
    }

    @Test
    public void testEquals() throws Exception {
        Hash hash1 = Hash.buildHash("adf");
        Assert.assertTrue(hash1.equals(hash1));
        Assert.assertFalse(hash1.equals(null));
        Hash hash2 = Hash.buildHash("adf");
        Assert.assertTrue(hash1.equals(hash2));
        Assert.assertTrue(hash1 == hash2);
    }
}
