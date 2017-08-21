import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@Nullable Object o) {
        if (o != null)
            for(Runnable r = () -> <selection>bar(o)</selection>; ; r.run()) {}
    }

    void bar(@NotNull Object o) {
    }
}