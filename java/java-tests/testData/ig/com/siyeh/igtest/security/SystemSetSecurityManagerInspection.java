package com.siyeh.igtest.security;

public class SystemSetSecurityManagerInspection
{
    public void foo()
    {
       System.setSecurityManager(null);
    }
}
