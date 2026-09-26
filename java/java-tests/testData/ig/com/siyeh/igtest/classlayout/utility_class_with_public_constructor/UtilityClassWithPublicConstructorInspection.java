package com.siyeh.igtest.classlayout.utility_class_with_public_constructor;

public class <warning descr="Class 'UtilityClassWithPublicConstructorInspection' has only 'static' members, and a 'public' constructor">UtilityClassWithPublicConstructorInspection</warning>
{
    public static final int CONSTANT = 1;

    public UtilityClassWithPublicConstructorInspection()
    {

    }

    public static int barangus()
    {
        return CONSTANT;
    }

    static class MyClass
    {
        public MyClass()
        {
        }
    }

    static class X {
        static int t = 9;
        int i = 0;

        public X () {}
    }
}
class NonUtility {
    private static final int Z = 10;
    public NonUtility() {}

    private static void boo() {}

}
