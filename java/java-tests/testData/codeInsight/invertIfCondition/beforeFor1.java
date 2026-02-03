// "Invert 'if' condition" "true"
class A {
    public void foo() {
        for (int i = 0;;i++) {
            <caret>if (c) a();
        }
    }
}