import org.jetbrains.annotations.Nullable;

class Test {
    public Number test(boolean b) {
        Double x = newMethod(b);
        if (x != null) return x;
        return 42l;
    }

    private @Nullable Double newMethod(boolean b) {
        if (b) {
            return 42.0;
        }
        if (!b) {
            return 42.0;
        }
        return null;
    }
}