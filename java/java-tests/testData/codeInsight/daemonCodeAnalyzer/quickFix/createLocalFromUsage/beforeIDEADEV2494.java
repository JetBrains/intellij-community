// "Create local variable 't'" "true"
class Test {
    private void f() {
        int i = <caret>t;
        t = 0;
    }

}