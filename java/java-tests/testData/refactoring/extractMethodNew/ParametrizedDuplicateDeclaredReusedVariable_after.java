import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a) {

        if (newMethod(a, 1)) return;
        String s;

        s = "";
        System.out.println(s.length());
    }

    private boolean newMethod(List<String> a, int i) {
        String s = a.get(i);
        if (s == null) return true;
        System.out.println(s.charAt(i));
        return false;
    }

    void bar(List<String> a) {
        if (newMethod(a, 2)) return;
        String s;
        s = "";
        System.out.println(s.length());
    }
}