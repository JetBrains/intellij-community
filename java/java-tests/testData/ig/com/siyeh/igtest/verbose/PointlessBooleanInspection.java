package com.siyeh.igtest.verbose;

public class PointlessBooleanInspection
{
    private boolean m_foo;
    private boolean m_bar;
    private static final boolean FALSE = false;

    public PointlessBooleanInspection(boolean foo)
    {

        this.m_foo = foo;
    }
    public void foo()
    {
        if(m_foo == true)
        {

        }
        if(m_foo == false)
        {

        }
        if(true == m_foo)
        {

        }
        if(false == m_foo)
        {

        }
        if(m_foo != true)
        {

        }
        if(m_foo != false)
        {

        }
        if(true != m_foo)
        {

        }
        if(FALSE != m_foo)
        {

        }
        if(m_bar == m_foo)
        {

        }

        if (m_foo || false) {

        }
        if (false || m_foo) {

        }

        if (m_foo && true) {

        }
        if (true && m_foo) {

        }
    }
}