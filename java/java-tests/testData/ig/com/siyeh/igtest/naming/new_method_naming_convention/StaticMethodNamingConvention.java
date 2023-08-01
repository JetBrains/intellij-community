package com.siyeh.igtest.naming.static_method_naming_convention;

public class StaticMethodNamingConvention
{
    public static void <warning descr="'static' method name 'UpperaseMethod' doesn't match regex '[a-z][A-Za-z\d]*'">UpperaseMethod</warning>()
    {

    }

    public static void methodNameEndingIn2()
    {

    }

    public static void <warning descr="'static' method name 'foo' is too short (3 < 4)">foo</warning>()
    {

    }

    public static void <warning descr="'static' method name 'methodNameTooLoooooooooooooooooooooooooooooooooooooooooooooong' is too long (62 > 32)">methodNameTooLoooooooooooooooooooooooooooooooooooooooooooooong</warning>()
    {

    }

    private static native void b();
}
