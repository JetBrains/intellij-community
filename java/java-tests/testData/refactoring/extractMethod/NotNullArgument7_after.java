import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void f(@Nullable Object o, boolean b) {
        while (o != null) {
            if (b) {
                o = 7;
            }
            newMethod(o);
        }
    }

    private void newMethod(@NotNull Object o) {
        g(o);
    }

    void g(Object o) {
    }
}