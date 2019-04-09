import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a) {

        NewMethodResult x = newMethod(a);
        if (x.exitKey == 1) return;
        String s;

        s = "";
        System.out.println(s.length());
    }

    NewMethodResult newMethod(List<String> a) {
        String s = a.get(1);
        if (s == null) return new NewMethodResult((1 /* exit key */), (null /* missing value */));
        System.out.println(s.charAt(1));
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private String s;

        public NewMethodResult(int exitKey, String s) {
            this.exitKey = exitKey;
            this.s = s;
        }
    }

    void bar(List<String> a) {
        String s = a.get(1);
        if (s == null) return;
        System.out.println(s.charAt(1));
        s = "";
        System.out.println(s.length());
    }
}