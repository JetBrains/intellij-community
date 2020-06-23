import java.util.List;

class C {
    List<String> x;
    List<String> y;

    private void foo() {

        newMethod(x, y);
    }

    private void newMethod(List<String> x, List<String> y) {
        if (x.isEmpty()) return;
        x.remove(0);
        y.add(str());
        baz();
    }

    private void bar() {
        newMethod(y, x);
    }

    private void baz() { }
    private String str() { return null; }
}