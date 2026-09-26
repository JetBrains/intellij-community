class C {
    int foo(String[] vars, int i) {
        return newMethod(vars[i]);
    }

    private int newMethod(String var) {
        return var.length();
    }
}