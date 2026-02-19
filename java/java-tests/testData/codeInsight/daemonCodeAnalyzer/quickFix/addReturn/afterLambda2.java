// "Add 'return' statement" "true-preview"
class C {
    void foo() {
        bar(() -> {
            return 1;
        });
    }

    void bar(I i) {}

    interface I {
        int f();
    }
}