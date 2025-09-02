// "Convert to record class" "true-preview"
record Point2(double x, double y) {
    Point2(double x) {
        this(x, 0);
        System.out.println("ctor 1: after fields are assigned");
    }

    // [after-only] Conversion to compact constructor will be suggested by RedundantRecordConstructor inspection
    Point2(double x, double y) {
        this.x = x;
        this.y = y;
        System.out.println("ctor 2: after fields are assigned");
    }
}
