// "Create local variable 't'" "true-preview"
class Test {
    private void f() {
        int i = <caret>t;
        t = 0;
    }

}