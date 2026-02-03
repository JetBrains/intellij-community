// "Create inner record 'Rect'" "true-preview"
class Test {
    void foo(Object obj) {
        switch (obj) {
            case Rect(Point point1, Point(double x, double y)) -> {}
            default -> {}
        }
    }

    private record Rect(Point point1, Point point) {
    }
}