// "Convert to record class" "true-preview"
record Point2(double x, double y) {
    Point2(double x) {
        this(x, 0);
    }

    Point2(String x) {
        this(Double.parseDouble(x), 0);
    }

    Point2(String x, String y) {
        this(Double.parseDouble(x), Double.parseDouble(y));
    }

    /// Classify: canonical, no redirect
    Point2 {
    }
}
