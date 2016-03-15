// "Copy 'i' to final temp variable" "false"
interface I {
    int f();
}

class C {
    void foo() {
        int i = new I() {
            public int f() {
                return <caret>i;
            }

        }.f();
    }
}
