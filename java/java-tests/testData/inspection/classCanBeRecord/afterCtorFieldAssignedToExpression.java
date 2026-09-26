// "Convert to record class" "true-preview"

record Point2(double x, double y) {
    Point2(double x, double y) {
        this.x = x;
        this.y = y + 1;
    }
}
