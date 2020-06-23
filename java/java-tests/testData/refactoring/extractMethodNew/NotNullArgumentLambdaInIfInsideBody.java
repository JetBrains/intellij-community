import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@Nullable Object o) {
        if (o != null)
            ((Runnable) (() -> <selection>bar(o)</selection>)).run();
    }

    void bar(@NotNull Object o) {
    }
}