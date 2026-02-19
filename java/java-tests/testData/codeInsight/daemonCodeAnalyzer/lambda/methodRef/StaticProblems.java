class MyTest {
    interface I {
       String m(Foo f);
    }

    static class Foo<X> {
       String foo() { return null; }

       Foo<X> getFoo() { return this; }

       static void test() {
          I i1 = Foo.<error descr="Non-static method 'getFoo()' cannot be referenced from a static context">getFoo</error>()::foo;
          I i2 = <error descr="'MyTest.Foo.this' cannot be referenced from a static context">this</error>::foo;
          I i3 = Foo :: foo;
       }
    }
}
class MyTest1 {
    void m1(MyTest1 rec, String x) { }
    void m1(String x) { }

    static void m2(String x) { }
    static void m2(MyTest1 rec, String x) {}

    void m3(MyTest1 rec, String x) { }
    static void m3(String x) { }

    void m4(String x) { }
    static void m4(MyTest1 rec, String x) { }

    interface I1 {
        void m(String x);
    }

    interface I2 {
        void m(MyTest1 rec, String x);
    }

    static void call1(I1 s) {   }

    static void call2(I2 s) {   }

    static void test1() {
        I1 s1 = <error descr="Non-static method cannot be referenced from a static context">MyTest1 ::m1</error>;
        call1(<error descr="Non-static method cannot be referenced from a static context">MyTest1::m1</error>);
        I1 s2 = MyTest1  :: m2;
        call1(MyTest1::m2);
        I1 s3 = MyTest1::m3;
        call1(MyTest1::m3);
        I1 s4 = <error descr="Non-static method cannot be referenced from a static context">MyTest1::m4</error>;
        call1(<error descr="Non-static method cannot be referenced from a static context">MyTest1::m4</error>);
    }

    static void test2() {
        I2 s1 = MyTest1 :: m1;
        call2(MyTest1::m1);
        I2 s2 = MyTest1 :: m2;
        call2(MyTest1::m2);

        I2 s3 = MyTest1 :: <error descr="Reference to 'm3' is ambiguous, both 'm3(MyTest1, String)' and 'm3(String)' match">m3</error>;
        call2(MyTest1::<error descr="Reference to 'm3' is ambiguous, both 'm3(MyTest1, String)' and 'm3(String)' match">m3</error>);
        I2 s4 = MyTest1 ::<error descr="Reference to 'm4' is ambiguous, both 'm4(MyTest1, String)' and 'm4(String)' match">m4</error>;
        call2(MyTest1::<error descr="Reference to 'm4' is ambiguous, both 'm4(MyTest1, String)' and 'm4(String)' match">m4</error>);
    }
}

class MyTest2 {
    void m1(String x) { }
    void m1(MyTest2 rec, String x) { }

    static void m2(MyTest2 rec, String x) {}
    static void m2(String x) { }

    static void m3(String x) { }
    void m3(MyTest2 rec, String x) { }

    static void m4(MyTest2 rec, String x) { }
    void m4(String x) { }

    interface I1 {
        void m(String x);
    }

    interface I2 {
        void m(MyTest2 rec, String x);
    }

    static void call1(I1 s) {   }

    static void call2(I2 s) {   }

    static void test1() {
        I1 s1 = <error descr="Non-static method cannot be referenced from a static context">MyTest2 ::m1</error>;
        call1(<error descr="Non-static method cannot be referenced from a static context">MyTest2::m1</error>);
        I1 s2 = MyTest2  :: m2;
        call1(MyTest2::m2);
        I1 s3 = MyTest2::m3;
        call1(MyTest2::m3);
        I1 s4 = <error descr="Non-static method cannot be referenced from a static context">MyTest2::m4</error>;
        call1(<error descr="Non-static method cannot be referenced from a static context">MyTest2::m4</error>);
    }

    static void test2() {
        I2 s1 = MyTest2 :: m1;
        call2(MyTest2::m1);
        I2 s2 = MyTest2 :: m2;
        call2(MyTest2::m2);

        I2 s3 = MyTest2 :: <error descr="Reference to 'm3' is ambiguous, both 'm3(MyTest2, String)' and 'm3(String)' match">m3</error>;
        call2(MyTest2::<error descr="Reference to 'm3' is ambiguous, both 'm3(MyTest2, String)' and 'm3(String)' match">m3</error>);
        I2 s4 = MyTest2 ::<error descr="Reference to 'm4' is ambiguous, both 'm4(MyTest2, String)' and 'm4(String)' match">m4</error>;
        call2(MyTest2::<error descr="Reference to 'm4' is ambiguous, both 'm4(MyTest2, String)' and 'm4(String)' match">m4</error>);
    }

    static void call3(I1 s) {}
    static void call3(I2 s) {}
    static {
          call3(MyTest2::m1);
          call3(MyTest2::<error descr="Reference to 'm2' is ambiguous, both 'm2(MyTest2, String)' and 'm2(String)' match">m2</error>);
          call3(MyTest2::m3);
          call3(MyTest2::<error descr="Reference to 'm4' is ambiguous, both 'm4(MyTest2, String)' and 'm4(String)' match">m4</error>);
    }
}

