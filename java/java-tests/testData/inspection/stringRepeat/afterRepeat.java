// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredSpaces() {
    StringBuilder sb = new StringBuilder();
      sb.append(" ".repeat(100));
    return sb.toString();
  }
}