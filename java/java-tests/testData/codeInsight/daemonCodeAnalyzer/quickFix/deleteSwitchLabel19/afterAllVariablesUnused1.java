// "Remove unreachable branches" "true"
import org.jetbrains.annotations.*;

class Test {
    void test(Object obj) {
        if (!(obj instanceof Rect)) return;
        System.out.println(42);
    }

    record Point(double x, double y) {}
    record Rect(@NotNull Point point1, @NotNull Point point2) {}
}
