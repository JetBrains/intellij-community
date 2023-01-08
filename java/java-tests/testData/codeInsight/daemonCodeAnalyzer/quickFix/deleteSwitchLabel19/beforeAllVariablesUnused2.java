// "Remove unreachable branches" "true"
class Test {
    void test(Object obj) {
        if (!(obj instanceof Rect)) return;
        switch (obj) {
            case Rect(Point(double x1, double y1) point1, Point(double x2, double y2))<caret>:
                System.out.println(42);
                break;
            default:
                break;
        }
    }

    record Point(double x, double y) {}
    record Rect(Point point1, Point point2) {}
}
