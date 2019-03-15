// "Replace with 'String.repeat()'" "true"
class Test {
  public int pendingSpaces;
  
  String testRepeat(StringBuilder buffer) {
    if (pendingSpaces > 0) {
      fo<caret>r (int sp = 0; sp < pendingSpaces; sp++)
        buffer.append(' ');
      pendingSpaces = 0;
    }
  }
}