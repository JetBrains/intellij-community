// "Replace with 'String.repeat()'" "true"
class Test {
  String testRepeat(String s, StringBuilder sb, int digits) {
    if ((s.length() < digits) && (sb.length() > 0)) {
        sb.append("0".repeat(digits - s.length()));
    }
  }
}