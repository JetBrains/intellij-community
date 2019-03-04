package pck;

interface A<T>
{
    T foo();
}

interface B<T> extends A<T[]> { }

class C<T extends A<Object[]> & B<Object>>
{
    void foo(T x)
    {
        Object[] foo = x.foo();
    }
}
