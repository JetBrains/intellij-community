public class TestClass {
    static TestClass getInstance() { return new TestClass(); }
    public int foo(TestClass p) {
        foo(get<caret>
        );
    }
}