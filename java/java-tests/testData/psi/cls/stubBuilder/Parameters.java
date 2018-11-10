class Parameters {
  class Inner {
    Inner(double doubleInnerParam, @Deprecated Object objectInnerParam) { }
  }

  enum Enum {
    E(0.0, "");

    Enum(double doubleEnumParam, @Deprecated String stringEnumParam) { }
  }

  interface Inter {
    void m(long longInterfaceParam, @Deprecated String stringInterfaceParam);
  }

  Parameters(long longConstructorParam, @Deprecated String stringConstructorParam) { }

  void foo(long longMethodParam, @Deprecated int intMethodParam) { }

  static void bar(Parameters objectStaticParam, @Deprecated int intStaticParam) { }
}