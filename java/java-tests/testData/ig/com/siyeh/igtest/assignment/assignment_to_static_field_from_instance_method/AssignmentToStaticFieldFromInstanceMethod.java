package com.siyeh.igtest.assignment.assignment_to_static_field_from_instance_method;

public class AssignmentToStaticFieldFromInstanceMethod {
    private static int x = 32;
    private int y = <warning descr="Assignment to static field 'x' from instance context">x</warning> = 8;
    private static int z = ++x;


    public void foo()
    {
        <warning descr="Assignment to static field 'x' from instance context">x</warning> = 3;
        <warning descr="Assignment to static field 'x' from instance context">x</warning>++;
        --<warning descr="Assignment to static field 'x' from instance context">x</warning>;
    }

    public static void foo2()
    {
        x = 3;
        x++;
        --x;
    }
}
