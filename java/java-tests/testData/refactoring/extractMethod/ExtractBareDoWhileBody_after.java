import org.jetbrains.annotations.Nullable;

class DoIfWhile {
    String foo(int a, boolean b) {
        int x = 0;
        do {
            String s = newMethod(b, x);
            if (s != null) return s;
        }
        while (++x < a);

        return null;
    }

    @Nullable
    private String newMethod(boolean b, int x) {
        /*comment*/
        if (b) {
            String s = bar(x);
            if (s != null) return s;
        }
        return null;
    }

    String bar(int x) { return "";}
}