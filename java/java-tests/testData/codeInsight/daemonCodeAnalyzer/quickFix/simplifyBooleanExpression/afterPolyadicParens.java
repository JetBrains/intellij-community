// "Simplify boolean expression" "true-preview"
class X {

    void test(int a, int b) {
        if (a + 1 + foo(a, b) > 5) {
        }

    }

    private int foo(int a, int b) {
        return a+b;
    }
}