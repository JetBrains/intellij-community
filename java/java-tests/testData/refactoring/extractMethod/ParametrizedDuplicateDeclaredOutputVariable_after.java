import org.jetbrains.annotations.Nullable;

import java.util.List;

class DeclaredOutputVariable {
    void foo(List<String> a) {

        String s = newMethod(a, 1);
        if (s == null) return;

        System.out.println(s.length());
    }

    @Nullable
    private String newMethod(List<String> a, int i) {
        String s = a.get(i);
        if (s == null) return null;
        System.out.println(s.charAt(i));
        return s;
    }

    void bar(List<String> a) {
        String s = newMethod(a, 2);
        if (s == null) return;
        System.out.println(s.length());
    }
}