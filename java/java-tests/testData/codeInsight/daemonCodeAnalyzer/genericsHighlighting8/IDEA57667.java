class A {
    <T> A(T x) {}

    {
        new <String>A<error descr="'A(java.lang.String)' in 'A' cannot be applied to '(int)'">(1)</error>;
    }
}
