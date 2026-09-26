// "Convert to record class" "true-preview"
record Point2(double x, double y) {
    Point2(double x) {
        this(x, 0);
        System.out.println("ctor 1: after fields are assigned");
    }

    Point2(double x, double y) {
        System.out.println("ctor 2: before fields are assigned");
        this.x = x;
        this.y = y;
        System.out.println("ctor 2: after fields are assigned");
    }
}
