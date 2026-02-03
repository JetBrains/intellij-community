// "Replace with 'StringBuffer.repeat()'" "true"
class Test {
  String hundredSpaces() {
    StringBuffer sb = new StringBuffer();
    f<caret>or(int i=0; i<100; i++) {
      sb.append(" ");
    }
    return sb.toString();
  }
}