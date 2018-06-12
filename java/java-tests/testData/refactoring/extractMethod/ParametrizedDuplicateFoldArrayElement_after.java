import org.jetbrains.annotations.Nullable;

class DeclaredOutputVariable {
    void foo(String[] a, int j) {

        String s = newMethod(a[j], 1, "X");
        if (s == null) return;

        System.out.println(s.length());
    }

    @Nullable
    private String newMethod(String s1, int i, String x) {
        String s = s1;
        if (s == null) return null;
        System.out.println(s.charAt(i) + x);
        return s;
    }

    void bar(String[] a, int k) {
        String s = newMethod(a[k], 2, "Y");
        if (s == null) return;
        System.out.println(s.length());
    }
}