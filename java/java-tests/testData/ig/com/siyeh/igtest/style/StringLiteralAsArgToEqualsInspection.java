package com.siyeh.igtest.style;

public class StringLiteralAsArgToEqualsInspection
{
    private String m_bar = "4";
    private boolean m_foo = m_bar.equals("3");

    public void foo()
    {
        if(m_bar.equals("3"))
        {

        }
        if(m_bar.equalsIgnoreCase("3"))
        {

        }
    }
}
