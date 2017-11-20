import java.util.List;

class ArgumentFoldingWholeStatement {
    List<String> x;
    List<String> y;

    private void foo() {
        newMethod();
    }

    private void newMethod() {
        x.add(str());
        baz();
    }

    private void bar() {
        y.add(str());
        baz();
    }

    private String str() { return null; }
    private void baz() { }
}
