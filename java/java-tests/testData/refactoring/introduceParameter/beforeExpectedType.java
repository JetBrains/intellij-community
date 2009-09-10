class Test {
    void f (String s) {}

    void u () {
        f(<selection>name</selection>);
    }

    void y () {
        String name = "";
        u();
    }
}
