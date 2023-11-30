package com.siyeh.igtest.bugs;

public class DefaultLastLabelInSwitchInspection
{
    private int m_bar;

    public DefaultLastLabelInSwitchInspection()
    {
        m_bar = 0;
    }

    public void foo()
    {
        final int bar = m_bar;
        switch(bar)
        {
            case 3:
            case 4:
                System.out.println("3");
                break;
            case 7:
                    System.out.println("bar");
                break;
            default:
                break;
            case 6:
                System.out.println("4");
                break;

        }
    }
}
