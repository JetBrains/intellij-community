import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@Nullable Object o) {
        if (o != null)
            for(Runnable r = () -> newMethod(o); ; r.run()) {}
    }

    private void newMethod(@NotNull Object o) {
        bar(o);
    }

    void bar(@NotNull Object o) {
    }
}