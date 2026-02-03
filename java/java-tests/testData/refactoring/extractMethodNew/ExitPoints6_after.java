import org.jetbrains.annotations.Nullable;

// nullable variable with flow statement
class K {
    int f(Object o) {
        o = newMethod(o);
        if (o == null) return 0;
        Object oo = o;

        return 1;
    }

    private @Nullable Object newMethod(Object o) {
        if (o == null) return null;
        o = new Object();
        return o;
    }
}
