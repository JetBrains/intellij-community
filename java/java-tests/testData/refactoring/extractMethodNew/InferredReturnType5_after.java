import org.jetbrains.annotations.Nullable;

class Test {
    public double test(boolean b, Integer notNullInt) {
        Double notNullInt1 = newMethod(b, notNullInt);
        if (notNullInt1 != null) return notNullInt1;
        return 42l;
    }

    private @Nullable Double newMethod(boolean b, Integer notNullInt) {
        if (b) {
            return (double) notNullInt;
        }
        if (!b) {
            return 42.0;
        }
        return null;
    }
}