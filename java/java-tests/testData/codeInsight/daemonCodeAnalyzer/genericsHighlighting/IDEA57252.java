class A<T> {
    class B extends A<<error descr="B is not accessible in current context">B.B</error>>{}
}