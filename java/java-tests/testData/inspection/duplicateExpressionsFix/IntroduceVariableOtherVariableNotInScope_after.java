class C {
  private static String toString(float... x) {
    String ret = null;
    for (float v : x) {
      String s = String.format("%f", v);
        String substring = s.substring(s.length() - 9);
        String substr = substring;
      if (ret == null) {
        ret = substring;
      }
      else {
        String substr2 = substring;
        ret += "," + substr2;
      }
    }
    return ret;
  }
}