// "Cast 1st argument to 'double'" "true-preview"
class a {
    void test()  {}
    void test(int i)  {}
    void test(String s)  {}
    void test(Object o)  {}
    void test(int i,double d)  {}
    void test(double d,int i)  {}

    void f() {
        test(<caret>(double) 0, 0);
    }
}

