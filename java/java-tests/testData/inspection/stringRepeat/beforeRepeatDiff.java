// "Replace with 'String.repeat()'" "true"
class Test {
  String spaces(int a, int b, int c, int d) {
    StringBuilder sb = new StringBuilder();
    f<caret>or(int i=a-b; i<c-d; i++) {
      sb.append(' ');
    }
    return sb.toString();
  }
}