package com.siyeh.igtest.style;

public class OnlyWarnArrayDimensions
{
    private int <warning descr="Variables with different array dimension in one declaration">m_fooBaz</warning>, m_fooBar[];

    public void fooBar()
    {
         int <warning descr="Variables with different array dimension in one declaration">fooBaz</warning>,   // comment1
                 fooBar[];   //comment2

         int i,j,k;
    }
}
