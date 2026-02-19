package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class AssertsWithoutMessagesInspection extends TestCase
{
    public AssertsWithoutMessagesInspection()
    {
    }

    public void test()
    {
        assertTrue(true);
        assertTrue("Barangus", true);
        assertEquals(true, false);
        assertEquals("Notorious JCKG", true, false);
        assertEquals("foo", "bar");
        assertEquals("message", "foo", "bar");
    }
}
