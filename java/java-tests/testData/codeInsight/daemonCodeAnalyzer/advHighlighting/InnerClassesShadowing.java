interface A
{
    interface B1  { }
}

class D implements A
{
    interface B1 { }
}


class C extends D implements A
{
    interface F extends <error descr="Reference to 'B1' is ambiguous, both 'D.B1' and 'A.B1' match">B1</error> { }
}

