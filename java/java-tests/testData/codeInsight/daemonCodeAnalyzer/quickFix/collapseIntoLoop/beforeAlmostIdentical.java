// "Collapse into loop" "true-preview"
class X {
    void test() {
        <caret>System.out.println("Hello");
        System.out.println("Hello1");
        System.out.println("Hello2");
        System.out.println("Hello");
        System.out.println("Hello");
        System.out.println("Hello");
    }
}