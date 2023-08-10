package com.siyeh.igtest.bugs;

public class MisspelledToStringInsspection
{
    private int m_bar;

    public MisspelledToStringInsspection()
    {
        m_bar = 0;
    }

    public String tostring()
    {
        return String.valueOf(m_bar == 3);
    }
}
