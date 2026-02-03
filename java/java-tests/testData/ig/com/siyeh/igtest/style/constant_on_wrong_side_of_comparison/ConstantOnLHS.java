package com.siyeh.igtest.style.constant_on_lhs;

public class ConstantOnLHS
{
    private  int m_bar = 4;
    private boolean m_foo = (<warning descr="Constant '3' on the left side of the comparison">3</warning> == m_bar);

    public void foo()
    {
        if(<warning descr="Constant '3' on the left side of the comparison">3</warning> == m_bar)
        {

        }
        if (4 ==<error descr="Expression expected"> </error>) {}
    }
}
