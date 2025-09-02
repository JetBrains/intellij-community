// "Convert to record class" "true-preview"

// Test for IDEA-371419 and IDEA-371645
record Point2(double x, double y) {
    Point2(double x, double y) {
        this.x = x;
        this.y = y;
        System.out.println("created");
    }
}
