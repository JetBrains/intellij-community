class Foo {
    void test(int a, int b) {
        <caret>if(a > 0) {
            if(b < 0) {
                System.out.println(a+b);
            } else {
                System.out.println(a-b);
            }
        }
    }
}