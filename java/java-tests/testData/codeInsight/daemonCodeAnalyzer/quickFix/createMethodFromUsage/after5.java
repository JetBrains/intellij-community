// "Create Method 'test'" "true"
public class Test {
    static void test(int i) {}
    public Test() {
        test();
    }

    private void test() {
        <caret><selection>//To change body of created methods use File | Settings | File Templates.</selection>
    }
}
