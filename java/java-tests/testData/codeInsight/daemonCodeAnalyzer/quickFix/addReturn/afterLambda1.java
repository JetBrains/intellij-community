// "Add 'return' statement" "true"
class C {
    void foo() {
        bar(() -> {
            return 0;
        });
    }

    void bar(I i) {}

    interface I {
        int f();
    }
}