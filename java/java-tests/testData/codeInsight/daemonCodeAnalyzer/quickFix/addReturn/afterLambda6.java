// "Add 'return' statement" "true"
class C {
    void foo() {
        bar(() -> {
            return Math.max(1,/*comment*/ 2);
        });
    }

    void bar(I i) {}

    interface I {
        int f();
    }
}