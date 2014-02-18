package pck;
import static pck.C.foo;
import static pck.D.foo;

class C {
     public static void foo(){}
}
class D {
     public static <T> void foo(){}
}

class B {
    {
        foo<error descr="Ambiguous method call: both 'C.foo()' and 'D.foo()' match">()</error>;
    }
}