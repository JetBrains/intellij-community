import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@NotNull Object o) {
        newMethod(o);
    }

    private void newMethod(@NotNull Object o) {
        Runnable r = () -> bar(o);
    }

    void bar(@NotNull Object o) {
    }
}