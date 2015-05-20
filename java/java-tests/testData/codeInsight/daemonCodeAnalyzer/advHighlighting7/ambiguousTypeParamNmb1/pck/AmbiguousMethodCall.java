package pck;
import static pck.D.foo;
import static pck.C.foo;

class C {
     public static <T> void foo(Comparable<T> x){}
}

class D {
    public static void foo(Comparable<?> x){}
}

class B{
    public static void bar(Comparable<?> x){
        foo<error descr="Ambiguous method call: both 'D.foo(Comparable<?>)' and 'C.foo(Comparable<capture of ?>)' match">(x)</error>;
    }
}