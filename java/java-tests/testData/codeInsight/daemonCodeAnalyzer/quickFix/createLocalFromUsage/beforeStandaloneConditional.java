// "Create local variable 't'" "true-preview"
class Test {
    private void f(boolean f) {
        <caret>t = f ? null : "";
    }

}