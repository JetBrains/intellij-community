import org.jetbrains.annotations.Nullable;

class Conditional {
    int bar(String s) {
        Integer n = newMethod(s);
        if (n != null) return n;
        return 0;
    }

    @Nullable
    private Integer newMethod(String s) {
        if (s != null) {
            int n = s.length;
            return n;
        }
        return null;
    }

    int baz(String z) {
        int x = -1;
        if (z != null) {
            int n = z.length;
            x = n;
        }
        return 0;
    }
}
