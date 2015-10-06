import org.jetbrains.annotations.Nullable;
class Test {
  @Nullable volatile String x;

  public void foo() {
    if (x != null) {
      System.out.println(<warning descr="Method invocation 'x.substring(1)' may produce 'java.lang.NullPointerException'">x.sub<caret>string(1)</warning>);
    }
  }
}