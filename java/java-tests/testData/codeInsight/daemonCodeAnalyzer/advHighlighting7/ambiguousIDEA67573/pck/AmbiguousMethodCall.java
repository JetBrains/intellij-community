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
        x.foo(null);
    }
}
