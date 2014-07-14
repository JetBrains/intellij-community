class Pos01<X> {

    Pos01(X x) {}

    <Z> Pos01(X x, Z z) {}

    void test() {
        Pos01<Integer> p1 = new Pos01<>(1);
        Pos01<? extends Integer> p2 = new Pos01<>(1);
        Pos01<?> p3 = new Pos01<>(1);
        Pos01<? super Integer> p4 = new Pos01<>(1);

        Pos01<Integer> p5 = new Pos01<>(1, "");
        Pos01<? extends Integer> p6 = new Pos01<>(1, "");
        Pos01<?> p7 = new Pos01<>(1, "");
        Pos01<? super Integer> p8 = new Pos01<>(1, "");
    }

    public static void main(String[] args) {
        Pos01<String> p1 = new Pos01<>("");
        p1.test();
    }
}