// "Add missing nested patterns" "true-preview"
class Main {
    void foo(Object obj) {
        switch (obj) {
            case Rect(Point(double x, double y), Point point2) -> {}
            default -> {}
        }
    }

    record Point(double x, double y) {}
    record Rect(Point point1, Point point2) {}
}
