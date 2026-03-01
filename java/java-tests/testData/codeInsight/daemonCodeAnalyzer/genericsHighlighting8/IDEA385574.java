class A {
    <T> A(T x) {}

    {
        new <Object>A("1");
        new <Integer>A<error descr="'A(java.lang.Integer)' in 'A' cannot be applied to '(java.lang.Number)'">((Number) 1.0f)</error>;

        new <Object>A("1") {};
        new <Integer>A<error descr="'A(java.lang.Integer)' in 'A' cannot be applied to '(java.lang.Number)'">((Number) 1.0f)</error> {};
    }
}