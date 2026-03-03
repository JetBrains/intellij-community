// "Convert to record class" "true"

// Converting this to record would create a new constructor (the record canonical constructor), which
// may not be desirable.

// Update as of 28/08/2025: in response to IDEA-375898, we now generate the record canonical constructor for this case.
record Point2(double x, double y) {
    Point2(double actuallyY) {
        this(0, actuallyY);
    }
}
