class Test {

    interface I1 {
       void m(String s);
    }

    interface I2 {
       void m(Integer s);
    }

    interface I3 {
       void m(Object o);
    }

    static class Foo<X extends Number> {
        Foo(X x) { }
    }

    static <X extends Number> void foo(X x) { }

    static void meth1(I1 s) { }

    static void meth2(I2 s) { }

    static void meth3(I3 s) { }

    static void meth4(I1 s) { }
    static void meth4(I2 s) { }
    static void meth4(I3 s) { }

    static {
        meth1(Foo::<error descr="Invalid constructor reference: String cannot be converted to Number">new</error>);
        meth2(Foo::new);
        meth3(Foo::<error descr="Invalid constructor reference: Object cannot be converted to Number">new</error>);
        meth4<error descr="Ambiguous method call: both 'Test.meth4(I1)' and 'Test.meth4(I2)' match">(Foo::new)</error>;

        meth1(Test::<error descr="Invalid method reference: String cannot be converted to X">foo</error>);
        meth2(Test::foo);
        meth3(Test::<error descr="Invalid method reference: Object cannot be converted to X">foo</error>);
        meth4<error descr="Ambiguous method call: both 'Test.meth4(I1)' and 'Test.meth4(I2)' match">(Test::foo)</error>;
    }


    <X extends Number> void fooInstance(X x) { }
    interface II1 {
        <X extends String> void m(X x);
    }
  
    interface II2 {
        <X extends Integer> void m(X x);
    }
  
    interface II3 {
        <X> void m(X x);
    }

    void test() {
      II1 i1 = this::<error descr="Invalid method reference: X cannot be converted to X">fooInstance</error>;
      II2 i2 = this::fooInstance;
      II3 i3 = this::<error descr="Invalid method reference: X cannot be converted to X">fooInstance</error>;
    }
}
