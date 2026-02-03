// "Convert to record class" "true-preview"
record Point2(double x, double y) {
    // Classify: non-canonical, redirect to canonical: this(double, double)
    Point2(double x) {
        this(x, 0);
    }

    // Classify: non-canonical, redirect to canonical: this(double, double)
    Point2(String x) {
        this(Double.parseDouble(x), 0);
    }

    // Classify: non-canonical, redirect to canonical: this(double, double)
    Point2(String x, String y) {
        this(Double.parseDouble(x), Double.parseDouble(y));
    }

    /// Classify: canonical, no redirect
    Point2 {
    }
}
