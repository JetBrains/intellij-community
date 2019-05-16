class Bar {
    private @mixed.Nullable Object[] typeUseAmbiguity(Object o) {
        @mixed.Nullable Object[] a = (@mixed.Nullable Object[]) o;
        if (a == null) {
            throw new NullPointerException();
        }
        return a;
    }

    private @<warning descr="@Nullable method 'noAmbiguity' always returns a non-null value">mixed.Nullable</warning> Object noAmbiguity(Object o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return o;
    }

    private @typeUse.Nullable Object[] typeUseArray(Object o) {
        @typeUse.Nullable Object[] a = (@typeUse.Nullable Object[]) o;
        if (a == null) {
            throw new NullPointerException();
        }
        return a;
    }

    private @<warning descr="@Nullable method 'typeUsePlain' always returns a non-null value">typeUse.Nullable</warning> Object typeUsePlain(Object o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return o;
    }
}
