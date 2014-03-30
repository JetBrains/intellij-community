interface A
{
    interface B  { }
}

interface C extends A, D<<error descr="B is not accessible in current context">C.B</error>> {}

interface D<T> {}