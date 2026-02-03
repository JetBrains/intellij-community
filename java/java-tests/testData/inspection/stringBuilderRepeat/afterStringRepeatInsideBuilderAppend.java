// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String hundredSpaces() {
    StringBuilder sb = new StringBuilder();
    sb.repeat(" ", 100);
    return sb.toString();
  }
}