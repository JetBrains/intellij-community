import java.util.List;

class ArgumentFoldingWholeStatement {
    List<String> x;
    List<String> y;

    private void foo() {
        <selection>x.add(str());
        baz();</selection>
    }

    private void bar() {
        y.add(str());
        baz();
    }

    private String str() { return null; }
    private void baz() { }
}
