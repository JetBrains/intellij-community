import java.util.HashMap;

class Test {
    void test() {
      java.util.Map<String, ClassThatDoesNotExistYet> map = new HashMap<String, ClassThatDoesNotExistYet>(<caret>);
    }
}