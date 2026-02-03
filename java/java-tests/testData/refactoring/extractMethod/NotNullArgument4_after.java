import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class C {
    void f() {
        I i = (@Nullable String o) -> {
            if (o != null) {
                newMethod(o);
            }
            return "";
        };

    }

    private void newMethod(@NotNull String o) {
        if (o instanceof String) {
            o = "4";
        }
        else {
            System.out.println(o);
        }
        g(o);
    }

    void g(@NotNull Object o) {
    }

    interface I<T, R> {
        R m(T t);
    }
}