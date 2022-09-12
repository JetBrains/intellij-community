// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredSpaces(StringBuilder sb) {
    f<caret>or(int i=0; i<100; i++) {
      sb.insert(123, " ");
    }
    return sb.toString();
  }
}