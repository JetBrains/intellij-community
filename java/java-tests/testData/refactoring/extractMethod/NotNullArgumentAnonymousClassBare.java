import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X {
    void foo(@NotNull Object o) {
        <selection>Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println(o);
            }
        };</selection>
    }
}
