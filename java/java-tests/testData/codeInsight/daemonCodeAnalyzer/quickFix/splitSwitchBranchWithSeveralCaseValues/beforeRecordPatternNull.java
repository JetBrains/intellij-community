// "Split values of 'switch' branch" "true-preview"
class C {
    void foo(Object o) {
        switch (o) {
            case Rect(Point(double x1, double y1) point1, Point(double x2, double y2) point2), nu<caret>ll -> {}
            default -> {}
        }
    }
}

record Point(double x, double y) {
}

record Rect(Point point1, Point point2) {
}