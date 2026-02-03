// "Invert 'if' condition" "true"
class A {
    public void foo() {
        while (true) {
            <caret>if (c) a();
        }
    }
}