import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@NotNull Object o) {
        Runnable r = () -> newMethod(o);
    }

    private void newMethod(@NotNull Object o) {
        bar(o);
    }

    void bar(@NotNull Object o) {
    }
}