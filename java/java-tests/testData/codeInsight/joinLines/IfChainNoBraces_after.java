class Foo {
    void test(int a, int b) {
        if(a > 0 <caret>&& b < 0) System.out.println(a + b);
    }
}