import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@Nullable Object o) {
        if (o != null) {
            <selection>Runnable r = () -> bar(o);</selection>
        }
    }

    void bar(@NotNull Object o) {
    }
}