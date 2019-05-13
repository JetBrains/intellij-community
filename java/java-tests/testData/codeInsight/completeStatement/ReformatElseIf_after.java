public class Test {
    public void foo(int x) {
        if (false) {
            return;
        } else if (true) {
            <caret>
        }
    }
}