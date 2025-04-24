// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873

// Test for IDEA-371419
record Point2(double x, double y) {
    Point2(double x, double y) {
        this.x = x;
        this.y = Math.sqrt(y);
    }
}
