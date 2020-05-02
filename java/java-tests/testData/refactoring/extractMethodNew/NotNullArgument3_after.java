import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void f() {
        I i = (@Nullable Object o) -> {
            if (o != null) {
                newMethod(o);
            }
        };

    }

    private void newMethod(@NotNull Object o) {
        if (o instanceof String) {
            o = 3;
        }
        else {
            System.out.println(o);
        }
        g(o);
    }

    void g(@NotNull Object o) {
    }

    interface I {
        void m(Object o);
    }
}