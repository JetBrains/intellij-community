// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String hundredSpaces() {
    StringBuilder sb = new StringBuilder();
    // comment before for-loop
      // comment before append
      sb.repeat(" ", 100);
    return sb.toString();
  }
}