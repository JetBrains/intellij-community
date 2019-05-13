class Foo {
    void test(int a, int b) {
        if(a > 0 && a < 10 <caret>&& b > 0 && b < 10) System.out.println(a + b);
    }
}