// "Replace with 'String.repeat()'" "true"
class Test {
  public int pendingSpaces;
  
  String testRepeat(StringBuilder buffer) {
    if (pendingSpaces > 0) {
        buffer.append(" ".repeat(pendingSpaces));
      pendingSpaces = 0;
    }
  }
}