// "Add 'return' statement" "true"
class C {
    void foo() {
        bar(() -> {
        <caret>});
    }

    void bar(I i) {}

    interface I {
        int f();
    }
}