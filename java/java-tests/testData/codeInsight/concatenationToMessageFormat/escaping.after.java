class C {
  private static String quote(String s) {
    return java.text.MessageFormat.format("\"{0}\"", s);
  }
}