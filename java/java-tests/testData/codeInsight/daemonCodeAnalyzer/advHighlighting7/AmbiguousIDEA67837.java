package pck;

import static pck.D.foo;
import static pck.C.foo;

class C {
     public static <T> void foo(Comparable<? extends Comparable<T>> x){}
}

class D {
    public static void foo(Comparable<? extends Number> x){}
}

class B{
    public static void bar(){
        foo<error descr="Ambiguous method call: both 'D.foo(Comparable<? extends Number>)' and 'C.foo(Comparable<? extends Comparable<Integer>>)' match">(1)</error>;
    }
}
