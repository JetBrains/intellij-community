import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public class A {
    void f() {
        String s = "";
        try {
            s = "a";
            s = newMethod(s);
        } finally {
            System.out.println(s);
        }
    }

    private @NotNull String newMethod(String s) {
        if (r()) throw new RuntimeException();
        s = "b";
        return s;
    }

    private boolean r() {
        return ThreadLocalRandom.current().nextBoolean();
    }
}