import org.jetbrains.annotations.Nullable;

class Test {
    public Number test(boolean b) {
        Double x = newMethod(b);
        if (x != null) return x;
        return 42l;
    }

    @Nullable
    private Double newMethod(boolean b) {
        if (b) {
            return (double) 42;
        }
        if (!b) {
            return 42.0;
        }
        return null;
    }
}