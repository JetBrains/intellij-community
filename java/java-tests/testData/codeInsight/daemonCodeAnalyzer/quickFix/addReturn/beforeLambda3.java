// "Add 'return' statement" "true-preview"
class C {
    void foo() {
        bar(() -> {
            Math.max(1, 2)
        <caret>});
    }

    void bar(I i) {}

    interface I {
        int f();
    }
}