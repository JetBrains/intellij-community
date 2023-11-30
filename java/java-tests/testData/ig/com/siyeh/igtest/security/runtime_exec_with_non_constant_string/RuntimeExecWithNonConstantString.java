package com.siyeh.igtest.security;

import java.io.IOException;

public class RuntimeExecWithNonConstantString
{
    public RuntimeExecWithNonConstantString()
    {
    }

    public void foo() throws IOException
    {
        String i = bar();
        final Runtime runtime = Runtime.getRuntime();
        runtime.<warning descr="Call to 'Runtime.exec()' with non-constant argument">exec</warning>("foo" + i);
        runtime.exec("foo");
    }

    private String bar() {
        return "bar";
    }
}