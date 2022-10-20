// "Remove unreachable branches" "true"
class Test {
    void test(Object obj) {
        if (!(obj instanceof Rect)) return;
        System.out.println(42);
    }

    record Point(double x, double y) {}
    record Rect(Point point1, Point point2) {}
}
