// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredSpaces() {
    StringBuilder sb = new StringBuilder();
      sb.insert(0, " ".repeat(100));
    return sb.toString();
  }
}