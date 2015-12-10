class MyTest {
    interface I1 {
       void m(String s);
    }

    interface I2 {
       void m(Integer s);
    }

    interface I3 {
       void m(Object o);
    }

    static <T extends Number> void m(T p) {}

    static <T1> void m1(T1 fx) { }
 
    static void foo(I1 i) {}
    static void foo(I2 i) {} //m
    static void foo(I3 i) {}

    static {
         foo(MyTest::<error descr="Cannot resolve method 'm'">m</error>);
         foo(MyTest::<error descr="Cannot resolve method 'm1'">m1</error>);
    }
}


class MyTest1 {
    interface I1 {
       void m(Integer s);
    }

    interface I2 {
       void m(Integer s);
    }

    static <T extends Number> void m(T p) { }
    static <T> void m1(T p) { }
 
    static void foo1(I1 i) { }
    static void foo2(I1 i) { }
    static void foo2(I2 i) { }

    static {
        foo1(MyTest1::m);
        foo2(MyTest1::<error descr="Cannot resolve method 'm'">m</error>);

        foo1(MyTest1::m1);
        foo2(MyTest1::<error descr="Cannot resolve method 'm1'">m1</error>);  
    }
}
