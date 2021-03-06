package todelete;

class MyTest {
    interface Bar1 {
        int m(String s);
    }

    interface Bar2 {
        int m(int s);
    }

    void bar(Bar1 b1){}
    void bar(Bar2 b1){}

    static int foo(int i) {
        return 0;
    }

    static int foo(String i) {
        return 0;
    }

    {
        Bar1 b1 = MyTest :: foo;
        bar(MyTest :: <error descr="Reference to 'foo' is ambiguous, both 'foo(int)' and 'foo(String)' match">foo</error>);
    }
}

class MyTest1 {
    interface Bar1 {
        int m(String s);
    }

    interface Bar2 {
        int m(int s);
    }

    //void bar(Bar1 b1){}
    void bar(Bar2 b1){}

    static int foo(int i) {
        return 0;
    }

    static int foo(String i) {
        return 0;
    }

    {
        Bar1 b1 = MyTest2 :: <error descr="Incompatible types: String is not convertible to int">foo</error>;
        bar(MyTest1 :: foo);
    }
}

class MyTest2 {
    interface Bar1 {
        int m(String s);
    }

    interface Bar2 {
        int m(int s);
    }

    void bar(Bar1 b1){}
    void bar(Bar2 b1){}

    static int foo(int i) {
        return 0;
    }

    /*static int foo(String i) {
        return 0;
    }*/

    {
        Bar1 b1 = MyTest2 :: <error descr="Incompatible types: String is not convertible to int">foo</error>;
        bar(MyTest2 :: foo);
    }
}

class MyTest3 {
    interface Bar1 {
        int m(String s);
    }

    interface Bar2 {
        int m(int s);
    }

    //void bar(Bar1 b1){}
    void bar(Bar2 b1){}

    /*static int foo(int i) {
        return 0;
    }*/

    static int foo(String i) {
        return 0;
    }

    {
        Bar1 b1 = MyTest2 :: <error descr="Incompatible types: String is not convertible to int">foo</error>;
        bar(MyTest3 :: <error descr="Incompatible types: int is not convertible to String">foo</error>);
    }
}


class MyTest4 {
    interface Bar1<T> {
        int m(T s);
    }


    void bar(Bar1 b1){}

    static int foo(int i) {
        return 0;
    }

    static int foo(String i) {
        return 0;
    }

    {
         bar(MyTest4:: <error descr="Reference to 'foo' is ambiguous, both 'foo(int)' and 'foo(String)' match">foo</error>);
    }
}

class MyTest5 {
    interface Bar1<T> {
        int m(T s);
    }


    static <T1> void bar(Bar1<T1> b1){}

    static <K> int foo(K k) {
        return 0;
    }

    static int foo(String i) {
        return 0;
    }

    {
         //todo ambiguity rules checked MyTest5.<String>bar(MyTest5::foo);
    }
}

class MyTest6 {
    interface I {
       void m(Integer i);
    }

    static void foo(Number i) {}
    static void foo(Integer i, String s) {}
    static void foo(Integer d) {}

    public static void main(String[] args) {
        I s = MyTest6::foo;
        s.m(1);
    }
}

class MyTest7 {
    interface I {
       void m(Number i);
    }

    static void foo(Number i) {}
    static void foo(Integer i, String s) {}
    static void foo(Integer d) {}

    public static void main(String[] args) {
        I s = MyTest7::foo;
        s.m(1);
    }
}
