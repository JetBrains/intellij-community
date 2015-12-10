class Test {

    interface I0 {
        void m();
    }

    interface I1<X> {
        void m(X x);
    }

    interface I2<X,Y> {
        void m(X x, Y y);
    }

    void m0() { }
    void m1(String s) { }
    void m2(String s1, String s2) { }

    void m01() { }
    void m01(String s) { }

    void m012() { }
    void m012(String s) { }
    void m012(String s1, String s2) { }

    static String instanceCall(I0 s) { return null; }
    static <T> T instanceCall(I1<T> s) { return null; }
    static <T> T instanceCall(I2<T, String> s) { return null; }

    {
        String i1 = instanceCall(this::m0);
        String i2 = instanceCall(this::m1);
        String i3 = instanceCall(this::m2);
        String i4 = instanceCall<error descr="Ambiguous method call: both 'Test.instanceCall(I0)' and 'Test.instanceCall(I1<String>)' match">(this::m01)</error>;
        String i5 = instanceCall<error descr="Ambiguous method call: both 'Test.instanceCall(I0)' and 'Test.instanceCall(I1<String>)' match">(this::m012)</error>;
    }

    void n0() { }
    void n1(String s) { }
    void n2(Test rec, String s2) { }

    void n01() { }
    void n01(String s) { }

    void n012() { }
    void n012(String s) { }
    void n012(Test rec, String s2) { }

    static <T> T staticCall(I1<T> s) { return null; }
    static <T> T staticCall(I2<T, String> s) { return null; }

    static {
        Test s1 = staticCall(Test::n0);
        Test s2 = staticCall(Test::n1);
        Test s3 = <error descr="Cannot resolve method 'staticCall(<method reference>)'">staticCall</error>(Test::n2);
        Test s4 = staticCall<error descr="Ambiguous method call: both 'Test.staticCall(I1<Test>)' and 'Test.staticCall(I2<Test, String>)' match">(Test::n01)</error>;
        Test s5 = staticCall<error descr="Ambiguous method call: both 'Test.staticCall(I1<Test>)' and 'Test.staticCall(I2<Test, String>)' match">(Test::n012)</error>;
    }
}