// "Convert to record class" "true"

// Test for IDEA-375898
class Main<caret> {
   final String part1;
   final String part2;

  Main(Integer part1, String part2) {
    this.part1 = part1.toString();
    this.part2 = part2;
  }

  String part1() {
    return part1;
  }

  String part2() {
    return part2;
  }
}