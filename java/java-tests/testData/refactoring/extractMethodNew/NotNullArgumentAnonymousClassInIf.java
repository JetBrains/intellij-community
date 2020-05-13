import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void foo(@Nullable Object o) {
        if (o != null) {
            <selection>Runnable r = new Runnable() {
                @Override
                public void run() {
                    bar(o);
                }
            };</selection>
        }
    }

    void bar(@NotNull Object o) {
    }
}