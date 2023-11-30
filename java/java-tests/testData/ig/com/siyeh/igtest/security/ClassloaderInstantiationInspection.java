package com.siyeh.igtest.security;

import java.net.URLClassLoader;

public class ClassloaderInstantiationInspection
{
    public void foo()
    {
       new URLClassLoader(null);
    }
}
