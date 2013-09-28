interface A
{
    abstract <T> void foo();
}

interface B
{
    abstract <T,S> void foo();
}

class C<<error descr="'foo()' in 'B' clashes with 'foo()' in 'A'; both methods have same erasure, yet neither overrides the other"></error>T extends A & B>
{
    void bar(T x)
    {
        x.foo<error descr="Ambiguous method call: both 'A.foo()' and 'B.foo()' match">()</error>;
    }
}
