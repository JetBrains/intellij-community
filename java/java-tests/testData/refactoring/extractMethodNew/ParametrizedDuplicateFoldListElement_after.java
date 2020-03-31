import org.jetbrains.annotations.Nullable;

import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a, int j) {

        String s = newMethod(a, j, 1, "X");
        if (s == null) return;

        System.out.println(s.length());
    }

    @Nullable
    private String newMethod(List<String> a, int j, int i, String x) {
        String s = a.get(j);
        if (s == null) return null;
        System.out.println(s.charAt(i) + x);
        return s;
    }

    void bar(List<String> a, int k) {
        String s = newMethod(a, k, 2, "Y");
        if (s == null) return;
        System.out.println(s.length());
    }
}