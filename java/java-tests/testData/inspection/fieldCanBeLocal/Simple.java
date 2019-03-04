class Test {
    private int <warning descr="Field can be converted to a local variable">f</warning>;

    void foo () {
        f = 0;
        int k = f;
    }
}
