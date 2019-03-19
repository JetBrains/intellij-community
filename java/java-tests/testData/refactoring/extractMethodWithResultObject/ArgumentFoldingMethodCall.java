import java.util.List;

class C {
    List<String> x;
    List<String> y;

    private void foo() {
        <selection>
        if (x.isEmpty()) return;
        x.remove(0);
        y.add(str());
        baz();</selection>
    }

    private void bar() {
        if (y.isEmpty()) return;
        y.remove(0);
        x.add(str());
        baz();
    }

    private void baz() { }
    private String str() { return null; }
}