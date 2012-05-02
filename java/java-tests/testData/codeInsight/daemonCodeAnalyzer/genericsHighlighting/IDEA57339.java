abstract class A {
    abstract <T extends Iterable & Cloneable> void foo();
}

abstract class B extends A{
    abstract <T extends Cloneable & Iterable> void foo();
}