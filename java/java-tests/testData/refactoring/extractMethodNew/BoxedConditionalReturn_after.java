import org.jetbrains.annotations.Nullable;

class Test {
    public static Integer foo(Integer[] a) {

        Integer n = newMethod(a);
        if (n != null) return n;
        return null;
    }

    @Nullable
    private static Integer newMethod(Integer[] a) {
        if (a.length != 0) {
            int n = a[0] != null ? a[0] : 0;
            return n;
        }
        return null;
    }

    public static Integer bar(Integer[] a) {
        Integer n = newMethod(a);
        if (n != null) return n;
        return null;
    }
}