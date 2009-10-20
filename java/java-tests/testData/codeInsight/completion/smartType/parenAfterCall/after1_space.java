public class TestClass {
    public void foo(TestClass p) {
        foo(this );<caret>
    }
}