import org.jetbrains.annotations.Nullable;

class C {
    void f(@Nullable Object o, boolean b) {
        while (o != null) {
            if (b) {
                o = 7;
            }
            <selection>g(o);</selection>
        }
    }

    void g(Object o) {
    }
}