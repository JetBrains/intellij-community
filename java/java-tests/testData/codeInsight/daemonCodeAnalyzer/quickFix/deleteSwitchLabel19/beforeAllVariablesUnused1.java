// "Remove unreachable branches" "true"
import org.jetbrains.annotations.*;

class Test {
    void test(Object obj) {
        if (!(obj instanceof Rect)) return;
        switch (obj) {
            case Rect(Point(double x1, double y1), Point(double x2, double y2)) rec<caret>:
                System.out.println(42);
                break;
            default:
                break;
        }
    }

    record Point(double x, double y) {}
    record Rect(@NotNull Point point1, @NotNull Point point2) {}
}
