import foo.Nullable;

class Bar {
    private @Nullable Object[] typeUseAmbiguity(Object o) {
        @Nullable Object[] a = (@Nullable Object[]) o;
        if (a == null) {
            throw new NullPointerException();
        }
        return a;
    }

    private @<warning descr="@Nullable method 'noAmbiguity' always returns a non-null value">Nullable</warning> Object noAmbiguity(Object o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return o;
    }
}
