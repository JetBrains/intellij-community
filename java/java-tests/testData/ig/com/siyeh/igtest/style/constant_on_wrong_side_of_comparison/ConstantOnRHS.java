package com.siyeh.igtest.style.constant_on_rhs;

public class ConstantOnRHS
{
    private int m_bar = 4;
    private boolean m_foo = (m_bar == <warning descr="Constant '3' on the right side of the comparison">3</warning>);

    public void foo()
    {
        if(m_bar == <warning descr="Constant '3' on the right side of the comparison">3</warning>)
        {

        }
        if (m_bar ==<error descr="Expression expected"> </error>) {}
        if (5 == m_bar) {}
    }

    boolean x(String s) {
        return s == null;
    }
}
class C {
    void t() {
        <error descr="Cannot resolve method 'method' in 'C'">method</error>(String.format("", <error descr="Expression expected">StringBuffer</error>)<error descr="',' or ')' expected">"</error>" +<EOLError descr="Expression expected"></EOLError>
                                              <<error descr="',' or ')' expected"><error descr="Expression expected">/</error></error><error descr="Cannot resolve symbol 'plugin'">plugin</error>><error descr="Illegal line end in string literal">" +</error>
        <error descr="Not a statement">""</error><error descr="Unexpected token">)</error>;
    }
}