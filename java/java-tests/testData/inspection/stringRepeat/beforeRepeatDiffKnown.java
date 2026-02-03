// "Replace with 'String.repeat()'" "true"
class Test {
  String testRepeat(String s, StringBuilder sb, int digits) {
    if ((s.length() < digits) && (sb.length() > 0)) {
      f<caret>or (int i=s.length(); i < digits; i++) {
        sb.append('0');
      }
    }
  }
}