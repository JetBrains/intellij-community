class InlineMethodTest {
    public void f<caret>oo(){}

    void test() throws Exception {
        System.out.println(InlineMethodTest.class.getDeclaredMethod("foo"));
    }
}
