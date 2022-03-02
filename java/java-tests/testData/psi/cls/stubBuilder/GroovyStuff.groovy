class GroovyStuff {
  private enum Enum {
    Value("", "");
    private Enum(String s1, @Deprecated String s2) { }
  }

  private class Inner {
    Inner(String s1, @Deprecated String s2) { }
  }

  GroovyStuff(long longConstructorParam, @Deprecated String stringConstructorParam) { }

  def foo(long longMethodParam, @Deprecated int intMethodParam) { }

  static def bar(GroovyStuff objectStaticParam, @Deprecated int intStaticParam) { }
}