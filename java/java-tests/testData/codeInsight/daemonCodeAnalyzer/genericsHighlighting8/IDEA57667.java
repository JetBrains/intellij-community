class A {
    <T> A(T x) {}

    {
        new <<error descr="Actual type argument and inferred type contradict each other">String</error>>A(1);
    }
}
