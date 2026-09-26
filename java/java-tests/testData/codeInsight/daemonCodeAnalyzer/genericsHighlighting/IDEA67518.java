class A<<warning descr="Type parameter 'T' is never used">T</warning> extends B.C>
{
    interface C {}
}

class B extends A<<warning descr="C is not accessible in current context">B.C</warning>>{}