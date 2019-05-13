class A<T extends A.B> {
    class B extends A<B> {}
}

class C<T> {
    class D extends C<T> {}
    <T extends C<String>.D> void foo(){}
}
