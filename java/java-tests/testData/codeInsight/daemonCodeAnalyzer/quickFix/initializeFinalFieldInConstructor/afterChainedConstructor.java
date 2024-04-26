// "Initialize in constructor" "true-preview"

class C {
    private final int a;
    private final int b;

    C() {
        this(1);
    }
    C(int a) {
        this.a = a;
        b = 0;
    }
}