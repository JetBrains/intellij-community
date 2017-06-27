import org.jetbrains.annotations.NotNull;

class C {
    void f(@NotNull Object o, boolean b) {
        if (b) {
            o = null;
        }
        <selection>g(o);</selection>
    }

    void g(Object o) {
    }
}