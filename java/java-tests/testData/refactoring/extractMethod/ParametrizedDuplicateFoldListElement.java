import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a, int j) {
        <selection>
        String s = a.get(j);
        if (s == null) return;
        System.out.println(s.charAt(1) + "X");
        </selection>
        System.out.println(s.length());
    }

    void bar(List<String> a, int k) {
        String s = a.get(k);
        if (s == null) return;
        System.out.println(s.charAt(2) + "Y");
        System.out.println(s.length());
    }
}