package pck;
import static pck.D.foo;
import static pck.C.foo;

class C {
     public static <T extends Comparable<S>, S> void foo(T x){}
}

class D {
     public static <T extends Comparable<?>> void foo(T x){}
}

class B{
    {
       foo<error descr="Ambiguous method call: both 'D.foo(Integer)' and 'C.foo(Integer)' match">(1)</error>;
    }
}
