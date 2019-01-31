// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredTimes(String s) {
    StringBuilder sb = new StringBuilder();
      sb.append(String.valueOf(s).repeat(100));
    return sb.toString();
  }
}