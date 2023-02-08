// "Remove unreachable branches" "true"
import org.jetbrains.annotations.*;

class Test {
    void foo(Object obj) {
        switch (obj) {
            case X x -> { }
            default -> { return; }
        }

        switch (obj) {
            case X<caret>(X(X(X(X x5)) x3)) x1 -> {
                System.out.println(x1);
                System.out.println(x3);
                System.out.println(x5);
            }
            default -> { }
        }
    }
}

record X(@NotNull X x) { }