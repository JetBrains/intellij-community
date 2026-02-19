// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String hundredSpaces() {
    String s = "*";
    StringBuilder sb = new StringBuilder();
    sb.repeat(s, 10);
    return sb.toString();
  }
}