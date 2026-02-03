interface A
{
    abstract void foo(String[] ... s);
}

interface B
{
    abstract void foo(String[] s);
}

class C<T extends A & B>
{
    void bar(T x)
    {
        x.foo<error descr="Ambiguous method call: both 'A.foo(String[]...)' and 'B.foo(String[])' match">(null)</error>;
    }
}
