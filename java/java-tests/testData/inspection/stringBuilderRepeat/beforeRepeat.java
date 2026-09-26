// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String hundredSpaces() {
    StringBuilder sb = new StringBuilder();
    // comment before for-loop
    f<caret>or(int i=0; i<100; i++) {
      // comment before append
      sb.append(" ");
    }
    return sb.toString();
  }
}