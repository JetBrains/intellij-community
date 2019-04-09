import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a) {

        NewMethodResult x = newMethod(a);
        if (x.exitKey == 1) return;
        int n;
        String s;

        s = "";
        n = 2;
        System.out.println(s.length() + n);
    }

    NewMethodResult newMethod(List<String> a) {
        int n = 1;
        String s = a.get(n);
        if (s == null)
            return new NewMethodResult((1 /* exit key */), (0 /* missing value */), (null /* missing value */));
        System.out.println(s.charAt(n));
        return new NewMethodResult((-1 /* exit key */), (0 /* missing value */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private int n;
        private String s;

        public NewMethodResult(int exitKey, int n, String s) {
            this.exitKey = exitKey;
            this.n = n;
            this.s = s;
        }
    }

    void bar(List<String> a) {
        int n = 1;
        String s = a.get(n);
        if (s == null) return;
        System.out.println(s.charAt(n));
        s = "";
        n = 2;
        System.out.println(s.length() + n);
    }
}