// "Convert to record class" "true-preview"
record Point3(double x, double y, double z) {
    Point3(double x) {
        this(x, 0, 0);
    }

    Point3(double x, double y) {
        this(x, y, 0);
    }

}
