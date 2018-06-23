import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a) {

        if (newMethod(a)) return;
        String s;
        int n;

        s = "";
        n = 2;
        System.out.println(s.length() + n);
    }

    private boolean newMethod(List<String> a) {
        int n = 1;
        String s = a.get(n);
        if (s == null) return true;
        System.out.println(s.charAt(n));
        return false;
    }

    void bar(List<String> a) {
        if (newMethod(a)) return;
        int n;
        String s;
        s = "";
        n = 2;
        System.out.println(s.length() + n);
    }
}