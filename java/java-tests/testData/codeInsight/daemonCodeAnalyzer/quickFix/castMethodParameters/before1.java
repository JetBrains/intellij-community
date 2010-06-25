// "Cast parameter to 'int'" "true"
class a {
    private void test()  {}
    private void test(int i)  {}
    private void test(String s)  {}
    private void test(Object o)  {}
    private void test(char c,char f)  {}

    void f() {
        test(<caret>2.2);
    }
}

