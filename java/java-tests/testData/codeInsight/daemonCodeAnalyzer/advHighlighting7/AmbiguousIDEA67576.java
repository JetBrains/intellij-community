package pck;

interface A<T>
{
     <S> T foo();
}

interface B
{
    <S> Object foo();
}

interface C extends A, B { }

class D
{
    void bar(C x)
    {
        x.foo();
    }
}
