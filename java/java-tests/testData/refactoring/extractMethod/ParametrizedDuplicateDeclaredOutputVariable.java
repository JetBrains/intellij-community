import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a) {
        <selection>
        String s = a.get(1);
        if (s == null) return;
        System.out.println(s.charAt(1));
        </selection>
        System.out.println(s.length());
    }

    void bar(List<String> a) {
        String s = a.get(2);
        if (s == null) return;
        System.out.println(s.charAt(2));
        System.out.println(s.length());
    }
}