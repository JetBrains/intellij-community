abstract class A
{
    abstract void foo();
}

interface B
{
    abstract void foo();
}

abstract class C extends A implements B { }
