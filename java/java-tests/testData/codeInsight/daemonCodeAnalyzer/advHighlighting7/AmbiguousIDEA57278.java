package pck;

interface I{
    <T extends Iterable<String> & Cloneable> void foo();
}

abstract class A {
    abstract <T extends Iterable<String>> void foo();
    <<error descr="'foo()' in 'pck.I' clashes with 'foo()' in 'pck.A'; both methods have same erasure, yet neither overrides the other"></error>T extends A & I> void bar(T x){
        x.foo<error descr="Ambiguous method call: both 'A.foo()' and 'I.foo()' match">()</error>;
    }
}
