// "Replace with 'StringBuilder.repeat()'" "true"
class Test {
  String hundredTimes(String s) {
    StringBuilder sb = new StringBuilder();
      sb.repeat(String.valueOf(s), 100);
    return sb.toString();
  }
}