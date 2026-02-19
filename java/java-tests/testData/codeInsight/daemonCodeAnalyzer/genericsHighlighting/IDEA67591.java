interface A
{
    interface B  { }
}

interface C extends A, D<<warning descr="B is not accessible in current context">C.B</warning>> {}

interface D<<warning descr="Type parameter 'T' is never used">T</warning>> {}