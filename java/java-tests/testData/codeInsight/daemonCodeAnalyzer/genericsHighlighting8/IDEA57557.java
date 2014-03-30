abstract class A {
    abstract void foo(Enum<?> x);
}

class B extends A {
    void foo(Enum<? extends Enum<?>> x) { }
}
