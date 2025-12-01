// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String hundredSpaces() {
    StringBuilder sb = new StringBuilder();
    sb.append<caret>(" ".repeat(100));
    return sb.toString();
  }
}