package com.siyeh.igtest.encapsulation.public_inner_class;

public class PublicInnerClass
{
    public class <warning descr="'public' nested class 'Barangus'">Barangus</warning>
    {

        public Barangus(int val)
        {
            this.val = val;
        }

        int val = -1;
    }

    public enum E {
        ONE, TWO
    }

    public interface I {

        void foo();
    }

}
