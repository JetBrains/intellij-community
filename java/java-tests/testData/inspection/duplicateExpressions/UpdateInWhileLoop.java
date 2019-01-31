class C {
  static String wrapMath(final String source) {
    final StringBuilder result = new StringBuilder();
    boolean inMath = false;
    int start = 0;
    boolean single;
    int end = source.indexOf("$");
    single = end + 1 >= source.length() || source.charAt(end + 1) != '$';
    while (end >= 0) {
      String substring = source.substring(start, end);
      if (start != 0) {
        result.append(escapeMath(inMath, single));
      }
      result.append(substring);

      inMath = !inMath;
      single = end + 1 >= source.length() || source.charAt(end + 1) != '$';
      start = end + (single ? 1 : 2);
      end = source.indexOf("$", start);
    }
    if (start != 0) {
      result.append(escapeMath(inMath, single));
    }

    String substring = source.substring(start);
    result.append(substring);
    return result.toString();
  }

  private static String escapeMath(boolean inMath, boolean single) {
    if (single) {
      return inMath ? "`$" : "$`";
    }
    else {
      return inMath ? "`$$" : "$$`";
    }
  }
}