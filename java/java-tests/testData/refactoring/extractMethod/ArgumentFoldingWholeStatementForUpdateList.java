import java.util.List;

class ArgumentFoldingWholeStatement {
    List<String> x;
    List<String> y;

    private void foo() {
        <selection>
        for (int i = 0; i < 5; i++, x.indexOf(str())) {
            baz();
        }</selection>
    }

    private void bar() {
        for (int i = 0; i < 5; i++, y.indexOf(str())) {
            baz();
        }
    }

    private String str() { return null; }
    private void baz() { }
}
