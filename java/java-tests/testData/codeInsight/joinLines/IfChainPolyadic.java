class Foo {
    void test(int a, int b) {
        <caret>if(a > 0 && a < 10) {
            if (b > 0 && b < 10) System.out.println(a + b);
        }
    }
}