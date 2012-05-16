// "Create Method 'test'" "true"
public class Test {
    public Test() {
        assert test();
    }

    private boolean test() {
        <caret><selection>return false;  //To change body of created methods use File | Settings | File Templates.</selection>
    }
}
