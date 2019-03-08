// "Create local variable 't'" "true"
class Test {
    private void f(boolean f) {
        <caret>t = f ? null : "";
    }

}