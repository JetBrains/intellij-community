package pck;

interface I{
    <T extends Iterable<String> & Cloneable> void foo();
}

abstract class A {
    abstract <T extends Iterable<String>> void foo();
    <T extends A & I> void bar(T x){
        x.foo<error descr="Ambiguous method call: both 'A.foo()' and 'I.foo()' match">()</error>;
    }
}
