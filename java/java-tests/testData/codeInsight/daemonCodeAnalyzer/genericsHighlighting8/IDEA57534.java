abstract class C{
    abstract <T extends Cloneable> void foo(T x);
    abstract <T extends Object & Cloneable> void foo(T x);
    void bar(Cloneable x){
        foo<error descr="Ambiguous method call: both 'C.foo(Cloneable)' and 'C.foo(Cloneable)' match">(x)</error>;
    }
}

abstract class D {
    abstract <T extends Iterable<? extends Exception>> void foo(T x);
    abstract <T extends Object & Iterable<? super Exception>> void foo(T x);
    void bar(Iterable<Exception> x){
        foo<error descr="Ambiguous method call: both 'D.foo(Iterable<Exception>)' and 'D.foo(Iterable<Exception>)' match">(x)</error>;
    }
}
