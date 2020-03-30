import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@Nullable Object o) {
        if (o != null)
            <selection>((Runnable)(() -> bar(o)))</selection>.run();
    }

    void bar(@NotNull Object o) {
    }
}