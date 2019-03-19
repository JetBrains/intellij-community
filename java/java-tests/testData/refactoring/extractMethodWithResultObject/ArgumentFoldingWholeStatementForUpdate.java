import java.util.List;

class ArgumentFoldingWholeStatement {
    List<String> x;
    List<String> y;

    private void foo() {
        <selection>
        for (int i = 0; ++i < 5; x.indexOf(str())) {
            baz();
        }</selection>
    }

    private void bar() {
        for (int i = 0; ++i < 5; y.indexOf(str())) {
            baz();
        }
    }

    private String str() { return null; }
    private void baz() { }
}
