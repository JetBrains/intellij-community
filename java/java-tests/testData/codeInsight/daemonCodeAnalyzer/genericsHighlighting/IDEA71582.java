class Main {
    static class C<T>
    {
        class D{}
    }

    void test()
    {
        C<String>.D o1 = null;
        C<Object>.D o2 = null;
        <error descr="Incompatible types. Found: 'Main.C<java.lang.Object>.D', required: 'Main.C<java.lang.String>.D'">o1 = o2</error>;
    }
}