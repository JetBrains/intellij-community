class MyTest1 {
    interface I {
        void m(int i1, int i2);
    }

    static void m1(int i1, int i2) { }
    static void m1(Integer i1, int i2) { }
    static void m1(int i1, Integer i2) { }
    static void m1(Integer i1, Integer i2) {}
    static void m1(Integer... is) { }
    
    static void m2(int... is) { }
    static void m2(double... ds) {}

    static void m3(int... is) { }
    static void m3(Object... ds) {}

    public static void main(String[] args) {
        I i1 = MyTest1::m1;
        i1.m(42,42);

        I i2 = MyTest1 :: m2;
        i2.m(42,42);

        I i3 = MyTest1 :: m3;  
    }
}

class MyTest {

    interface I1 {
        void m(int i);
    }

    interface I2 {
        void m(MyTest t, int i);
    }

    static void static_1(Integer i) {}
    static void static_2(Integer i1, Integer i2) {}
    static void static_3(String s) {}
    static void static_4(String... ss) {}

    void _1(Integer i) {}
    void _2(Integer i1, Integer i2) {}
    void _3(String s) {}
    void _4(String... ss) {}

    static {
        I1 i1 = MyTest::static_1;
        I1 i2 = MyTest::<error descr="Cannot resolve method 'static_2'">static_2</error>;
        I1 i3 = MyTest::<error descr="Invalid method reference: int cannot be converted to String">static_3</error>;
        I1 i4 = MyTest::<error descr="Cannot resolve method 'static_4'">static_4</error>;
    }


    {
        I1 i_1 = <error descr="Non-static method cannot be referenced from a static context">MyTest::_1</error>;
        I1 i_2 = <error descr="Non-static method cannot be referenced from a static context">MyTest::_2</error>;
        I1 i_3 = <error descr="Non-static method cannot be referenced from a static context">MyTest::_3</error>;
        I1 i_4 = MyTest::<error descr="Cannot resolve method '_4'">_4</error>;

        I1 i1 = this::_1;
        I1 i2 = this::<error descr="Cannot resolve method '_2'">_2</error>;
        I1 i3 = this::<error descr="Invalid method reference: int cannot be converted to String">_3</error>;
        I1 i4 = this::<error descr="Cannot resolve method '_4'">_4</error>;

        I2 i21 = MyTest::<error descr="Cannot resolve method 'm1'">m1</error>;
        I2 i22 = MyTest::<error descr="Cannot resolve method 'm2'">m2</error>;
        I2 i23 = MyTest::<error descr="Cannot resolve method 'm3'">m3</error>;
        I2 i24 = MyTest::<error descr="Cannot resolve method 'm4'">m4</error>;
    }
}

class MyTest2 {

    interface IBool { void m(boolean[] a); }
    interface IInt { void m(int[] a); }
    interface IDbl { void m(double[] a); }
    interface IObj { void m(Object[] a); }

    static void foo(Object... vi) {}

    static {
        IBool iBool = MyTest2::foo;
        IInt iInt = MyTest2::foo;
        IDbl iDbl = MyTest2::foo;
        IObj iObj = MyTest2::foo;
    }
}

