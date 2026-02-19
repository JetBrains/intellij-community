// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  public int pendingSpaces;
  
  String testRepeat(StringBuilder buffer) {
    if (pendingSpaces > 0) {
        buffer.repeat(" ", pendingSpaces);
      pendingSpaces = 0;
    }
  }
}