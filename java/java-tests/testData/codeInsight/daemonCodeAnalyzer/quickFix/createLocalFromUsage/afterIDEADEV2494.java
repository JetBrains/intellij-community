// "Create local variable 't'" "true"
class Test {
    private void f() {
        int t<caret>;
        int i = t;
        t = 0;
    }

}