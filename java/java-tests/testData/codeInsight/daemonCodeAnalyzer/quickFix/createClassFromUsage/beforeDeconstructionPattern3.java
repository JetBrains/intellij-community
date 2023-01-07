// "Create record 'Rect'" "true-preview"
class Test {
    void foo(Object obj) {
        switch (obj) {
            case Re<caret>ct(Point point1, Point(double x, double y)) -> {}
            default -> {}
        }
    }
}