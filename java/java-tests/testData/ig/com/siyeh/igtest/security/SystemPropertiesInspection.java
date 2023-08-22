package com.siyeh.igtest.security;

public class SystemPropertiesInspection
{
    public void foo()
    {
       System.getProperties();
       System.getProperty("foo");
       Integer.getInteger("foo");
       Boolean.getBoolean("foo");
    }
}
