// "Add 'return' statement" "true-preview"
class C {
    void foo() {
        bar(() -> {
            return Math.max(1, 2);//comment
        });
    }

    void bar(I i) {}

    interface I {
        int f();
    }
}