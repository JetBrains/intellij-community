import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@Nullable Object o) {
        if (o != null)
            ((Runnable) (() -> newMethod(o))).run();
    }

    private void newMethod(@NotNull Object o) {
        bar(o);
    }

    void bar(@NotNull Object o) {
    }
}