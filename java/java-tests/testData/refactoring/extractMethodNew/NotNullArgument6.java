import org.jetbrains.annotations.Nullable;

class C {
    void f(@Nullable Object o, boolean b) {
        if (b) {
            o = null;
        }
        <selection>g(o);</selection>
    }

    void g(Object o) {
    }
}