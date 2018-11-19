class C {
  private static String toString(float... x) {
    String ret = null;
    for (float v : x) {
      String s = String.format("%f", v);
      String substr = s.substring(s.length() - 9);
      if (ret == null) {
        ret = <caret>s.substring(s.length() - 9);
      }
      else {
        ret += "," + s.substring(s.length() - 9);
      }
    }
    return ret;
  }
}