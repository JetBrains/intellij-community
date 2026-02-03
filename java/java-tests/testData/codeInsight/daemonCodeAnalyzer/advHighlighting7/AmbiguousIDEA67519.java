package pck;
class A<T extends C<String> & D>
{
    void bar(T x)
    {
        x.foo<error descr="Ambiguous method call: both 'C.foo(String)' and 'D.foo(String)' match">("")</error>;
    }
}

interface D
{
    abstract void foo(String s);
}

interface C<T>
{
     abstract void foo(T s);
}