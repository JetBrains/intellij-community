package com.siyeh.igtest.classlayout.utility_class_without_private_constructor;


public class <warning descr="Class 'UtilityClassWithoutPrivateConstructorInspection' has only 'static' members, and lacks a 'private' constructor">UtilityClassWithoutPrivateConstructorInspection</warning>
{
    public static final int CONSTANT = 1;
    public static int barangus()
    {
        return CONSTANT;
    }
}
class Temp
{
    public static void main(String[] arg)
    {
        System.out.println(getValue());
    }

    private static boolean getValue()
    {
        return false;
    }

    private static class Util {

        public static void foo() {}
    }
}