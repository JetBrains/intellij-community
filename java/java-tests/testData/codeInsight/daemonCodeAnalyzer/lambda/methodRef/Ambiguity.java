package todelete;

/**
 * Created with IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 9/26/12
 * Time: 1:18 PM
 * To change this template use File | Settings | File Templates.
 */
class MyTest {
    interface Bar1 {
        int _(String s);
    }

    interface Bar2 {
        int _(int s);
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
        bar<error descr="Ambiguous method call: both 'MyTest.bar(Bar1)' and 'MyTest.bar(Bar2)' match">(MyTest :: foo)</error>;
    }
}

class MyTest1 {
    interface Bar1 {
        int _(String s);
    }

    interface Bar2 {
        int _(int s);
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
        <error descr="Incompatible types. Found: '<method reference>', required: 'todelete.MyTest1.Bar1'">Bar1 b1 = MyTest2 :: foo;</error>
        bar(MyTest1 :: foo);
    }
}

class MyTest2 {
    interface Bar1 {
        int _(String s);
    }

    interface Bar2 {
        int _(int s);
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
        <error descr="Incompatible types. Found: '<method reference>', required: 'todelete.MyTest2.Bar1'">Bar1 b1 = MyTest2 :: foo;</error>
        bar(MyTest2 :: foo);
    }
}

class MyTest3 {
    interface Bar1 {
        int _(String s);
    }

    interface Bar2 {
        int _(int s);
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
        <error descr="Incompatible types. Found: '<method reference>', required: 'todelete.MyTest3.Bar1'">Bar1 b1 = MyTest2 :: foo;</error>
        bar<error descr="'bar(todelete.MyTest3.Bar2)' in 'todelete.MyTest3' cannot be applied to '(<method reference>)'">(MyTest3 :: foo)</error>;
    }
}


class MyTest4 {
    interface Bar1<T> {
        int _(T s);
    }


    void bar(Bar1 b1){}

    static int foo(int i) {
        return 0;
    }

    static int foo(String i) {
        return 0;
    }

    {
         bar<error descr="'bar(todelete.MyTest4.Bar1)' in 'todelete.MyTest4' cannot be applied to '(<method reference>)'">(MyTest4:: foo)</error>;
    }
}

class MyTest5 {
    interface Bar1<T> {
        int _(T s);
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
       void _(Integer i);
    }

    static void foo(Number i) {}
    static void foo(Integer i, String s) {}
    static void foo(Integer d) {}

    public static void main(String[] args) {
        I s = MyTest6::foo;
        s._(1);
    }
}

class MyTest7 {
    interface I {
       void _(Number i);
    }

    static void foo(Number i) {}
    static void foo(Integer i, String s) {}
    static void foo(Integer d) {}

    public static void main(String[] args) {
        I s = MyTest7::foo;
        s._(1);
    }
}
