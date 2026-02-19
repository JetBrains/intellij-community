// "Replace with 'StringBuffer.repeat()'" "true"
class Test {
  String hundredSpaces() {
    StringBuffer sb = new StringBuffer();
      sb.repeat(" ", 100);
    return sb.toString();
  }
}