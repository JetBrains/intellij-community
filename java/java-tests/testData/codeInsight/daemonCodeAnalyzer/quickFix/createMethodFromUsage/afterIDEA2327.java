// "Create method 'test'" "true-preview"
public class Test {
    public Test() {
        assert test();
    }

    private boolean test() {
        <caret><selection>return false;</selection>
    }
}
