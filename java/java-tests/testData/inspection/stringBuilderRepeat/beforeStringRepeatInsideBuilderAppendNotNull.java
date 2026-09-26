// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String hundredSpaces() {
    String s = "*";
    StringBuilder sb = new StringBuilder();
    sb.append<caret>(s.repeat(10));
    return sb.toString();
  }
}