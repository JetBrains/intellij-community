// "Convert to record class" "true"

// Test for IDEA-375898
record Main(String part1, String part2) {
    Main(Integer part1, String part2) {
        this(part1.toString(), part2);
    }
}