import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void f(@Nullable Object o) {
        if (o != null) {<selection>
            if (o instanceof String) {
                o = 1;
            } else {
                System.out.println(o);
            }
            g(o);</selection>
        }
    }

    void g(@NotNull Object o) {
    }
}