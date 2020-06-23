import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@NotNull Object o) {
        Runnable r = () -> <selection>bar(o)</selection>;
    }

    void bar(@NotNull Object o) {
    }
}