interface I {
  void m1() <error descr="Deprecated extension method syntax">default</error> { }

  default void m2() <error descr="Deprecated extension method syntax">default</error> { }

  @SuppressWarnings("extensionSyntax")
  void m3() default { }
}