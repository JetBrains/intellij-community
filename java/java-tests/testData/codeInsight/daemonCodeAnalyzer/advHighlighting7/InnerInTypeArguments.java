class A<T extends <error descr="'A.B' has private access in 'A'">A<T>.B</error>> {
    private class B {}
}
