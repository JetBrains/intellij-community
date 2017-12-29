import org.jetbrains.annotations.Nullable;

class ForIf {
    String foo(int[] a, boolean b) {
        for (int x : a) {
            String s = newMethod(b, x);
            if (s != null) return s;
        }

        return null;
    }

    @Nullable
    private String newMethod(boolean b, int x) {
        if (b) {
            String s = bar(x);
            if (s != null) return s;
        }
        return null;
    }

    String bar(int x) { return "";}
}
