// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String testRepeat(String s, StringBuilder sb, int digits) {
    if ((s.length() < digits) && (sb.length() > 0)) {
        sb.repeat("0", digits - s.length());
    }
  }
}