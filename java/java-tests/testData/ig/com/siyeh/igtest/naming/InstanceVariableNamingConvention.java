package com.siyeh.igtest.naming;

public class InstanceVariableNamingConvention
{
    public final int lowercaseConstant = 2;
    private final int s_lowercaseConstant = 2;
    private  int m_lowercaseConstant = 2;

    public void fooBar()
    {
        System.out.println("lowercaseConstant = " + lowercaseConstant);
        System.out.println("m_lowercaseConstant = " + m_lowercaseConstant);
        System.out.println("s_lowercaseConstant = " + s_lowercaseConstant);
    }
}
