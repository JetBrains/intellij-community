// "Convert to record class" "true"

// Converting this to record would create a new constructor (the record canonical constructor), which
// may not be desirable.

// Update as of 28/08/2025: in response to IDEA-375898, we now generate the record canonical constructor for this case.
record R(int x, int y, int z) {
    R(int x, int y) {
        this(x, y, 10);
    }
}
