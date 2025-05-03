// "Convert to record class" "true-preview"

record Point2(double x, double y) {
    Point2(double x, double y) {
        this.y = Math.abs(x) + Math.sqrt(y);
        this.x = x;
    }
}
