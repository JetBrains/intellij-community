class C {
    String[] vars;
    int foo(C c, int i) {
        return newMethod(c.vars[i]);
    }

    private int newMethod(String var) {
        return var.length();
    }
}