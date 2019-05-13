class A<T extends B.C>
{
    interface C {}
}

class B extends A<<error descr="C is not accessible in current context">B.C</error>>{}