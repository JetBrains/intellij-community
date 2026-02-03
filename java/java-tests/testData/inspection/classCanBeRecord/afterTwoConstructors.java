// "Convert to record class" "true-preview"

record Point(double x, double y) {
    Point(double x) {
        this(x, 0);
    }

    // During conversion to record, this constructor will become a canonical constructor, and then be removed because it's redundant.
    // Removal of a redundant canonical constructor is actually an inspection on its own: RedundantRecordConstructorInspection.
}
