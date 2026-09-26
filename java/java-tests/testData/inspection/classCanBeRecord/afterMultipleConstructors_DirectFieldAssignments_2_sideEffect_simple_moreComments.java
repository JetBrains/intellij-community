// "Convert to record class" "true-preview"
record Point2(double x, double y) {
    // This is an additional constructor.
    Point2(double x) {
        // Before
        // After assign X
        /* crazy */
        this(x, 0); // After assign Y
        System.out.println("ctor 1: after fields are assigned");
        // After
    }

    /// This is a canonical constructor.
    Point2 {
        // Before
        System.out.println("ctor 2: before fields are assigned");
        // In the middle
        // After
    }
}
