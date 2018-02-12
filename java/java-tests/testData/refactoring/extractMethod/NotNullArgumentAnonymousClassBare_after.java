import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X {
    void foo(@NotNull Object o) {
        newMethod(o);
    }

    private void newMethod(@NotNull Object o) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println(o);
            }
        };
    }
}
