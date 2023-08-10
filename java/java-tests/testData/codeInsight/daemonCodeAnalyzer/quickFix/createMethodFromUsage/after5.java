// "Create method 'test'" "true-preview"
public class Test {
    static void test(int i) {}
    public Test() {
        test();
    }

    private void test() {
        <caret><selection></selection>
    }
}
