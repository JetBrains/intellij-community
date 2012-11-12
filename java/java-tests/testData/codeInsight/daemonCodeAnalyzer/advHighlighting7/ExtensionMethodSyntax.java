interface I {
  void m1() <warning descr="Deprecated extension method syntax">default</warning> { }

  default void m2() <warning descr="Deprecated extension method syntax">default</warning> { }

  @SuppressWarnings("extensionSyntax")
  void m3() default { }
}