// "Convert to record class" "true-preview"

record Point(double x, double y) {
    Point(double x) {
        this(x, 0);
    }

    @Deprecated
    Point {
    }
}
