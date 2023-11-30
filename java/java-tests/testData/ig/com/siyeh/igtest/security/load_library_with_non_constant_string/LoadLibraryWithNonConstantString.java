package com.siyeh.igtest.security;

import java.io.IOException;

public class LoadLibraryWithNonConstantString extends ClassLoader
{
    public LoadLibraryWithNonConstantString()
    {
    }

    public void foo() throws IOException
    {
        String i = bar();
        System.<warning descr="Call to 'System.loadLibrary()' with non-constant argument">loadLibrary</warning>("foo" + i);
        System.loadLibrary("foo");
        System.<warning descr="Call to 'System.load()' with non-constant argument">load</warning>(i);
    }

    private String bar() {
        return "bar";
    }

    private void abc() {
        String s = bar();
        Runtime.getRuntime().<warning descr="Call to 'Runtime.load()' with non-constant argument">load</warning>(s);
        Runtime.getRuntime().<warning descr="Call to 'Runtime.loadLibrary()' with non-constant argument">loadLibrary</warning>(s);
    }
}