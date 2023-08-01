package com.siyeh.igtest.junit;

import org.junit.Before;
import org.junit.BeforeClass;

public class BeforeOrAfterWithBadSignatureInspection {
    @Before
    public void foo2()
    {

    }

    @BeforeClass
    public  void foobar()
    {

    }

    @Before
    public void afterfoo2()
    {

    }

    @BeforeClass
    public static void afteroobar()
    {

    }
}
