class RenamedParameter {
    private boolean c;

    public void foo() {
        String a = "s";
        String b = "t";
        if (c) {
            String t = b;
            x(t);
        } else if (!b.equals(a)) {
            x(b);
        }
    }//ins and outs
//in: PsiLocalVariable:a
//in: PsiLocalVariable:b
//exit: SEQUENTIAL PsiMethod:foo

    public NewMethodResult newMethod(String b, String a) {
        if (c) {
            String t = b;
            x(t);
        } else if (!b.equals(a)) {
            x(b);
        }
        return new NewMethodResult();
    }

    public class NewMethodResult {
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
