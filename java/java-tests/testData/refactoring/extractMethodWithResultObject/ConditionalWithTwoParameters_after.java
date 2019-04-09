class RenamedParameter {
    private boolean c;

    public void foo() {
        String a = "s";
        String b = "t";
        NewMethodResult x = newMethod(b, a);
    }

    NewMethodResult newMethod(String b, String a) {
        if (c) {
            String t = b;
            x(t);
        } else if (!b.equals(a)) {
            x(b);
        }
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    public void bar() {
        String a = "t";
        String b = "s";
        if (c) {
            String t = b;
            x(t);
        } else if (!b.equals(a)) {
            x(b);
        }
    }

    void x(String s) {}
}
