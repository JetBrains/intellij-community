// "Convert to record class" "true-preview"

record Point2(double x, double y) {
    Point2(double x, double y) {
        this.x = x;
        this.y = Math.abs(y) + Math.sqrt(x);
    }
}
