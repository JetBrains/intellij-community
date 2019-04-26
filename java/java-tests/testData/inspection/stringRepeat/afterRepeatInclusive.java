// "Replace with 'String.repeat()'" "true"
class Test {
  String spaces(int c, int d) {
    StringBuilder sb = new StringBuilder();
      sb.append("123456".repeat(Math.max(0, c - d)));
    return sb.toString();
  }
}