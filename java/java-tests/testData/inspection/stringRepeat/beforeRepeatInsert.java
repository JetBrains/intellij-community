// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredSpaces() {
    StringBuilder sb = new StringBuilder();
    f<caret>or(int i=0; i<100; i++) {
      sb.insert(0, " ");
    }
    return sb.toString();
  }
}