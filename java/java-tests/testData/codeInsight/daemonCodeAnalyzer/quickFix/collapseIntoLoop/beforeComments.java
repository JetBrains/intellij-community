// "Collapse into loop" "true"
class X {
    void test() {
        // 1
        <caret>System.out.println("Hello");
        // 2
        System.out./*2.0*/println("Hello"+/*2.1*/"world");
        // 3
        System.out.println("Hello1");
        // 4
    }
}