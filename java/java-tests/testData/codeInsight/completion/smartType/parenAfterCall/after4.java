public class TestClass {
    public int foo(TestClass p) {
        bar(foo(this)<caret>)
    }
}