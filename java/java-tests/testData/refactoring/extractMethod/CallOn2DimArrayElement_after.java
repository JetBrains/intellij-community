class C {
    int foo(String[][] vars, int i, int j) {
        return newMethod(vars[i][j]);
    }

    private int newMethod(String s) {
        return s.length();
    }
}