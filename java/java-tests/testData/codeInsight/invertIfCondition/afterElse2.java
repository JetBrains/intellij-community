// "Invert 'if' condition" "true"
class A {
    public void foo() {
        <caret>if (!c) {
            b();
        }
    }
}