import org.jetbrains.annotations.Nullable;

class DeclaredOutputVariable {
    void foo(String[] a) {

        String s = newMethod(a, 1);
        if (s == null) return;

        System.out.println(s.length());
    }

    @Nullable
    private String newMethod(String[] a, int i) {
        String s = a[i];
        if (s == null) return null;
        System.out.println(s.charAt(i));
        return s;
    }

    void bar(String[] a) {
        String s = newMethod(a, 2);
        if (s == null) return;
        System.out.println(s.length());
    }
}