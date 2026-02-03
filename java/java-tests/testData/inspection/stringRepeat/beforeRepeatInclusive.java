// "Replace with 'String.repeat()'" "true"
class Test {
  String spaces(int c, int d) {
    StringBuilder sb = new StringBuilder();
    f<caret>or(int i=1; i<=c-d; i++) {
      sb.append(123_456);
    }
    return sb.toString();
  }
}