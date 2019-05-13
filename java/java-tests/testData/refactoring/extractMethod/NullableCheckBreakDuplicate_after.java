import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NullableCheckBreakDuplicate {
    List<Pojo> things;

    void foo() {
        while (true) {

            Pojo x = newMethod();
            if (x == null) break;
            System.out.println(x.it);
        }
    }

    @Nullable
    private Pojo newMethod() {
        Pojo x = things.get(0);

        if (x.it > 0) return null;
        things.remove(x);
        return x;
    }

    void baz() {
        while (true) {
            Pojo x = newMethod();
            if (x == null) break;
            System.out.println(x.it);
        }
    }

    static class Pojo {
        double it;
        Pojo(double w) { it = w; }
    }
}
