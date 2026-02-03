import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void f(@NotNull Object o, boolean b) {
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