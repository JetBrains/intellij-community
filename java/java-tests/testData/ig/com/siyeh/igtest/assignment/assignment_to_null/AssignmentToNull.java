package com.siyeh.igtest.bugs;

public class AssignmentToNull
{
    private Object m_foo = null;

    public static void main(String[] args)
    {
        new AssignmentToNull(new Object()).bar();
        <warning descr="'null' assigned to variable 'args[0]'">args[0]</warning> = null;
    }

    public AssignmentToNull(Object foo)
    {
        m_foo = foo;
    }

    public void bar()
    {
        Object foo = new Object();
        System.out.println("foo = " + foo);
        <warning descr="'null' assigned to variable 'foo'">foo</warning> = null;
        <warning descr="'null' assigned to variable 'm_foo'">m_foo</warning> = null;
        System.out.println("foo = " + foo);
        System.out.println("m_foo = " + m_foo);
    }
}