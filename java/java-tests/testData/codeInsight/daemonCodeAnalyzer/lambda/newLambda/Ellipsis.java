interface Var {
    void var(int... ps);
}

class Abc {
    void foo() {
        Var var = (int[] ps) -> {};
    }
}