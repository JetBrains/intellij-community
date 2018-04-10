import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@Nullable Object o) {
        while (o != null)
            newMethod(o).run();
    }

    @NotNull
    private Runnable newMethod(@NotNull Object o) {
        return (Runnable)(() -> bar(o));
    }

    void bar(@NotNull Object o) {
    }
}