import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a) {

        if (newMethod(a)) return;
        String s;

        s = "";
        System.out.println(s.length());
    }

    private boolean newMethod(List<String> a) {
        String s = a.get(1);
        if (s == null) return true;
        System.out.println(s.charAt(1));
        return false;
    }

    void bar(List<String> a) {
        if (newMethod(a)) return;
        String s;
        s = "";
        System.out.println(s.length());
    }
}