class X  {

  public static void buildRegexp(String pattern, int exactPrefixLen, boolean forCompletion) {
    System.out.println(!forCompletion && pattern.endsWith(" "));
    System.out.println(exactPrefixLen == 0);
    boolean prevIsUppercase = false;
    for (int i = 0; i < pattern.length(); i++) {
      final char c = pattern.charAt(i);
      if (c == '*') { }
      else if (c == ' ') { }
      else if (c == ':' || prevIsUppercase) { }
    }
    System.out.println(forCompletion);
    System.out.println(exactPrefixLen);
  }

}
