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

// IDEA-365762
class Test {
  private final @Nullable Object finalField;

  public Test(@Nullable Object finalField) {
    this.finalField = finalField;
  }

  public void test() {
    if (finalField != null) {
      finalField.toString();
      nop();
      finalField.toString(); 
    }
  }

  private void nop() {
  }
}