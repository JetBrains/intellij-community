package com.siyeh.igtest.threading.field_accessed_synchronized_and_unsynchronized;

class MutualRecursion
{
    private final Object m_lock = new Object();          
    private Object <warning descr="Field 'm_contents' is accessed in both synchronized and unsynchronized contexts">m_contents</warning> = new Object();

    public void foo()
    {
        synchronized(m_lock)
        {
            m_contents = new Object();
        }
        getContents();
    }

    private Object getContents()
    {
        getContents2();
        return m_contents;
    }

    private void getContents2() {
        getContents();
    }

}
