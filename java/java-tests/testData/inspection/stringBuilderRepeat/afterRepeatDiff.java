// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String spaces(int a, int b, int c, int d) {
    StringBuilder sb = new StringBuilder();
      sb.repeat(" ", Math.max(0, c - d - (a - b)));
    return sb.toString();
  }
}