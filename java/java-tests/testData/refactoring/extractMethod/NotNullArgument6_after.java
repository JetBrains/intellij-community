import org.jetbrains.annotations.Nullable;

class C {
    void f(@Nullable Object o, boolean b) {
        if (b) {
            o = null;
        }
        newMethod(o);
    }

    private void newMethod(@Nullable Object o) {
        g(o);
    }

    void g(Object o) {
    }
}