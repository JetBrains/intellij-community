// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String hundredSpaces(String s, int i) {
    StringBuilder sb = new StringBuilder();
    sb.append<caret>(s.repeat(i));
    return sb.toString();
  }
}