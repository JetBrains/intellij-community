import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@Nullable Object o) {
        if (o != null)
            newMethod(o).run();
    }

    @NotNull
    private Runnable newMethod(@NotNull Object o) {
        return new Runnable() {
            @Override
            public void run() {
                bar(o);
            }
        };
    }

    void bar(@NotNull Object o) {
    }
}