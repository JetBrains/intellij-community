// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredTimes(String s) {
    StringBuilder sb = new StringBuilder();
    f<caret>or(int i=0; i<100; i++) {
      sb.append(s);
    }
    return sb.toString();
  }
}