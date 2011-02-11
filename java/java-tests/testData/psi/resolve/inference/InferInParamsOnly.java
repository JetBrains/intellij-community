class C {
    class I {}
    class A extends I {}
    class B extends I {}

    <T> T f(T t1, T t2) {return null;}

    void foo () {
        Object o = <ref>f (new A(), new B ());
    }
}