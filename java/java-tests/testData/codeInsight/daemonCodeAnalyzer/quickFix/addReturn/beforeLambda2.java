// "Add 'return' statement" "true"
class C {
    void foo() {
        bar(() -> {
            1
        <caret>});
    }

    void bar(I i) {}

    interface I {
        int f();
    }
}