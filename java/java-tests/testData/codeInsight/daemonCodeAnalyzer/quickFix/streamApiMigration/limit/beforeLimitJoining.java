// "Collapse loop with stream 'collect()'" "false"

class X {
  public static String getAbbreviation(String s) {
    String[] words = s.split(" ");
    StringBuilder sb = new StringBuilder();
    <caret>for (String word : words) {
      sb.append(word).append(" ");
      if (sb.length() >= 70) {
        return sb + " ...";
      }
    }
    return sb + " ...";
  }
}