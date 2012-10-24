class A {
    <T> A() {}

    {
        new <<error descr="Type argument cannot be of primitive type">int</error>>A();
    }
}

class B<T> {
    {
        new B<<error descr="Type argument cannot be of primitive type">int</error>>();
        B.<<error descr="Type argument cannot be of primitive type">int</error>>m();
    }

    <S> void m(){}

}