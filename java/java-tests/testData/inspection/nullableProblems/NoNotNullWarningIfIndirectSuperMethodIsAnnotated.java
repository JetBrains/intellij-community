import org.jetbrains.annotations.NotNull;

class Foo {
  interface I {
    // Warning: "Overriding method parameters are not annotated", expected
    void m(<warning descr="Overriding method parameters are not annotated">@NotNull</warning> String s);
  }

  static class S implements I {
    @Override
    // Warning: "Not annotated parameter overrides @NotNull parameter", expected
    public void m(String <warning descr="Not annotated parameter overrides @NotNull parameter">s</warning>) {
      System.out.println(s);
    }
  }

  static class C extends S {
    @Override
    // Warning: "Parameter annotated @NotNull should not override non-annotated parameter"
    // undesired if we have no control over the S superclass.
    // Having annotation is still preferred here
    public void m(@NotNull String s) {
      super.m(s);
    }
  }
}