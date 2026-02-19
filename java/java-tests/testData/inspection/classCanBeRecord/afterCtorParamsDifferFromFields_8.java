// "Convert to record class" "true-preview"

// Test for IDEA-371419
record Point2(double x, double y) {
    Point2(double x, double y) {
        this.x = x;
        this.y = Math.sqrt(y);
    }
}
