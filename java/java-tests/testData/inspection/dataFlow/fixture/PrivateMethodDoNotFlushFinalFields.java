import org.jetbrains.annotations.Nullable;

abstract class Foo {

  private final Bar bar;

  @Nullable
  public Object foo() {
    return bar != null ? bar.a().getObj(getIt(), bar.b()) : null;
  }

  protected Foo(@Nullable Bar bar) {
    this.bar = bar;
  }

  private String getIt() {
    return "it";
  }
}

record Bar(A a, B b) {
}
record A() {
  Object getObj(String s, B b) {return new Object();}
}
record B() {}