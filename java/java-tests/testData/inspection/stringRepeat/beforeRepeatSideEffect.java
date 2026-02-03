// "Replace with 'String.repeat()'" "false"
class Test {
  String hundredNumbers() {
    StringBuilder sb = new StringBuilder();
    f<caret>or(int i=0; i<100; i++) {
      sb.append(Math.random());
    }
    return sb.toString();
  }
}