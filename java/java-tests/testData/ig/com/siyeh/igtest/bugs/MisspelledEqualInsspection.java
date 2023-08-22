package com.siyeh.igtest.bugs;

public class MisspelledEqualInsspection
{
    private int m_bar;

    public MisspelledEqualInsspection()
    {
        m_bar = 0;
    }

    public boolean equal(Object foo)
    {
        return m_bar == 3;
    }
}
