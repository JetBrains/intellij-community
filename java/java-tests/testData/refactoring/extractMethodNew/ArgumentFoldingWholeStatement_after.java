import java.util.List;

class ArgumentFoldingWholeStatement {
    List<String> x;
    List<String> y;

    private void foo() {
        newMethod(x);
    }

    private void newMethod(List<String> x) {
        x.add(str());
        baz();
    }

    private void bar() {
        newMethod(y);
    }

    private String str() { return null; }
    private void baz() { }
}
