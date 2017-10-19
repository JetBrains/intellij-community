class RenamedParameter {
    private boolean c;

    public void foo() {
        String a = "s";
        String b = "t";
        newMethod(a, b);
    }

    private void newMethod(String a, String b) {
        if (c) {
            String t = b;
            x(t);
        } else if (!b.equals(a)) {
            x(b);
        }
    }

    public void bar() {
        String a = "t";
        String b = "s";
        newMethod(a, b);
    }

    void x(String s) {}
}
