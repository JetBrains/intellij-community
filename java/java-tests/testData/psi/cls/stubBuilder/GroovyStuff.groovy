class GroovyStuff {
  private enum Enum {
    Value("", "");
    private Enum(String s1, @Deprecated String s2) { }
  }

  private class Inner {
    Inner(String s1, @Deprecated String s2) { }
  }
}