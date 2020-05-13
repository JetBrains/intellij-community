class Main {
    private String [] args;

    void foo(Main m, int i) {
        newMethod(m, i);

    }

    private void newMethod(Main m, int i) {
        if (m.args[i] != null) {
            System.out.println(m.args[i]);
        }
    }
}
