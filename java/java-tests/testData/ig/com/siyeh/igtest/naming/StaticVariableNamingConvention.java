package com.siyeh.igtest.naming;

public class StaticVariableNamingConvention
{
    public static  int lowercaseConstant = 2;
    private static  int s_lowercaseConstant = 2;
    private static  int m_lowercaseConstant = 2;

    public static void fooBar()
    {
        System.out.println("lowercaseConstant = " + lowercaseConstant);
        System.out.println("m_lowercaseConstant = " + m_lowercaseConstant);
        System.out.println("s_lowercaseConstant = " + s_lowercaseConstant);
    }
}
