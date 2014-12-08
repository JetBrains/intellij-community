class Main {
    private String [] args;

    void foo(Main m, int i) {
        newMethod(m.args[i]);

    }

    private void newMethod(String arg) {
        if (arg != null) {
            System.out.println(arg);
        }
    }
}
