class MyTest {
    MyTest() {
    }

    interface I {
        MyTest m();
    }

    static void test(I s) {
        s.m();
    }

    public static void main(String[] args) {
        I s = MyTest::new;
        s.m();
        test(MyTest::new);
    }
}

class MyTest1 {

    MyTest1(Object o) {
    }

    MyTest1(Number n) {
    }

    interface I {
        MyTest1 m(Object o);
    }

    static void test(I s, Object arg) {
        s.m(arg);
    }

    public static void main(String[] args) {
        I s = MyTest1::new;
        s.m("");
        test(MyTest1::new, "");
    }
}

class MyTest2<X> {
    MyTest2(X x) {
    }

    interface I<Z> {
        MyTest2<Z> m(Z z);
    }

    static <Y> void test(I<Y> s, Y arg) {
        s.m(arg);
    }

    public static void main(String[] args) {
        I<String> s = MyTest2<String>::new;
        s.m("");
        test(MyTest2<String>::new, "");
    }
}

class MyTest3<X> {

    MyTest3(X x) { }

    interface I<Z> {
        MyTest3<Z> m(Z z);
    }

    static void test(I<Integer> s) {   }

    public static void main(String[] args) {
        <error descr="Incompatible types. Found: '<method reference>', required: 'MyTest3.I<java.lang.Integer>'">I<Integer> s = MyTest3<String>::new;</error>
        test<error descr="'test(MyTest3.I<java.lang.Integer>)' in 'MyTest3' cannot be applied to '(<method reference>)'">(MyTest3<String>::new)</error>;
    }
}
