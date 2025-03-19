package com.siyeh.igtest.confusing;

import java.util.List;

public class AssignmentToForLoopParameter
{
    public AssignmentToForLoopParameter()
    {
    }

    public void fooBar(int bar, int baz)
    {

        for(int i = 0; i < 5; i++)
        {
            for(int j = 0; j < 5; <warning descr="Assignment to for-loop parameter '(i)'">(i)</warning>++)
            {

            }
        }
        for(int j = 0; j < 5; j++)
        {
            <warning descr="Assignment to for-loop parameter 'j'">j</warning> = 2;
        }
        for(int k = 0; k < 5; k++)
        {
            <warning descr="Assignment to for-loop parameter 'k'">k</warning>++;
            <warning descr="Assignment to for-loop parameter 'k'">k</warning>--;
            ++<warning descr="Assignment to for-loop parameter 'k'">k</warning>;
            --<warning descr="Assignment to for-loop parameter 'k'">k</warning>;
        }
    }

    void test(int bound) {
        for(int i=0; i<bound;) {
            int step = calculateStep();
            System.out.println(step);
            i+=step; // Assignment to for-loop parameter 'i'
        }
    }
    native int calculateStep();

    void list(List<String> strings) {
        for (String string : strings) {
            (<warning descr="Assignment to for-loop parameter 'string'">string</warning>) = "";
            System.out.println("string = " + string);
        }
    }
}
