// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String spaces(int c, int d) {
    StringBuilder sb = new StringBuilder();
      sb.repeat("123456", Math.max(0, c - d));
    return sb.toString();
  }
}