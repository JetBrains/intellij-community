import java.util.List;

class C {
    List<String> x;
    List<String> y;

    private void foo() {

        NewMethodResult x1 = newMethod();
        if (x1.exitKey == 1) return;
    }

    NewMethodResult newMethod() {
        if (x.isEmpty()) return new NewMethodResult((1 /* exit key */));
        x.remove(0);
        y.add(str());
        baz();
        return new NewMethodResult((-1 /* exit key */));
    }

    static class NewMethodResult {
        private int exitKey;

        public NewMethodResult(int exitKey) {
            this.exitKey = exitKey;
        }
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