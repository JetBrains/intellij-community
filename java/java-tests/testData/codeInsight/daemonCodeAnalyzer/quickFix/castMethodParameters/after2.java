// "Cast 1st argument to 'char'" "true-preview"
class a {
    private void test()  {}
    private void test(int i)  {}
    private void test(String s)  {}
    private void test(Object o)  {}
    private void test(char c,char f)  {}

    void f() {
        test(<caret>(char) 2.2, 'd');
    }
}

