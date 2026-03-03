// "Convert to record class" "true"

// Converting this to record would create a new constructor (the record canonical constructor), which
// may not be desirable.

// Update as of 28/08/2025: in response to IDEA-375898, we now generate the record canonical constructor for this case.
record R(int first) {
    R(int first, int second) {
        this(first);
    }
}
