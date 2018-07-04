import org.jetbrains.annotations.Nullable;

class ElseIf {
    String foo(boolean a, boolean b) {
        if (a) {
            String s = newMethod(b);
            if (s != null) return s;
        }

        return null;
    }

    @Nullable
    private String newMethod(boolean b) {
        if (b) {
            String s = bar();
            if (s != null) return s;
        }
        return null;
    }

    String bar() { return "";}
}
