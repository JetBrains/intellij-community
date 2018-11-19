class C {
  private static String toString(float... x) {
    String ret = null;
    for (float v : x) {
      String s = String.format("%f", v);
        String substring = s.substring(s.length() - 9);
        if (ret == null) {
        ret = substring;
      }
      else {
        ret += "," + substring;
      }
    }
    return ret;
  }
}