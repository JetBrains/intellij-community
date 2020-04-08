import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void f(@Nullable Object o) {
        if (o != null) {
            <selection>g(o);</selection>
        }
    }

    void g(@NotNull Object o) {
    }
}