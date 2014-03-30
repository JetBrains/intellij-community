package pck;

import java.io.Serializable;

abstract class A {
    abstract <T extends Comparable<?> & Serializable> void foo(T x, Integer y);
    abstract <T extends Serializable & Comparable<?>> void foo(T x, Object y);

    {
        foo("", 1);
    }
}