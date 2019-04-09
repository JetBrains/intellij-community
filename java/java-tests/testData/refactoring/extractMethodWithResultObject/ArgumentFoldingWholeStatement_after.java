import java.util.List;

class ArgumentFoldingWholeStatement {
    List<String> x;
    List<String> y;

    private void foo() {
        NewMethodResult x1 = newMethod();
    }

    NewMethodResult newMethod() {
        x.add(str());
        baz();
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    private void bar() {
        y.add(str());
        baz();
    }

    private String str() { return null; }
    private void baz() { }
}
