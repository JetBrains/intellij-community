class A<<warning descr="Type parameter 'T' is never used">T</warning>> {
    class B extends A<<warning descr="B is not accessible in current context">B.B</warning>>{}
}