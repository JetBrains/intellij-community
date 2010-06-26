// "Cast parameter to 'a'" "true"
class a {
    void test()  {}
    void test(int i)  {}
    void test(String s)  {}
    void test(a o)  {}
    void test(int i,double d)  {}
    void test(double d,int i)  {}

    void f(Runnable r) {
        test(<caret>(a) r);
    }
}

