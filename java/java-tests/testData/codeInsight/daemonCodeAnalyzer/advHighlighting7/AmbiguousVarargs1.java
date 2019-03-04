package pck;

abstract class A {
    abstract <T> void foo(T... y);
    abstract <T> void foo(T[]... y);
    void bar(){
        foo();
    }
}