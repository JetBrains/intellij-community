// "Create method 'test'" "true"
public class Test {
    static void test(int i) {}
    public Test() {
        test();
    }

    private void test() {
        <caret><selection></selection>
    }
}
