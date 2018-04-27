import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a) {
        <selection>
        int n = 1;
        String s = a.get(n);
        if (s == null) return;
        System.out.println(s.charAt(n));
        </selection>
        s = "";
        n = 2;
        System.out.println(s.length() + n);
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