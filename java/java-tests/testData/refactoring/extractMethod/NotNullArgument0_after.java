import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void f(@Nullable Object o) {
        if (o != null) {
            newMethod(o);
        }
    }

    private void newMethod(@NotNull Object o) {
        g(o);
    }

    void g(@NotNull Object o) {
    }
}