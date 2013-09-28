interface IA<T> {
    IA<? extends T> foo();
}

class A {
    void baz(IA<? extends Throwable> x) {
        bar(x.foo());
    }

    <T extends Throwable> void bar(IA<T> a) {
    }
}
