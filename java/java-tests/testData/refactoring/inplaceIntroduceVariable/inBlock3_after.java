class C {
    private int bar() {
        return 42;
    }
    public void foo() {
        int i1 = bar() + 1;
        if (true) {
            if (false) {
                int i = i1;
                int j = bar() - 1;
            } else {
                int i = i1;
                int j = i1;
            }
        } else {
            if (true) {
                int i = i1;
                int j = i1;
            } else {
                int i = i1;
                int j = i1;
            }
        }
    }
}