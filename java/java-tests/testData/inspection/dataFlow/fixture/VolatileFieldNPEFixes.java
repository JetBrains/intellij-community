import org.jetbrains.annotations.Nullable;
class Test {
  @Nullable volatile String x;

  public void foo() {
    if (x != null) {
      System.out.println(x.<warning descr="Method invocation 'substring' may produce 'java.lang.NullPointerException'">subs<caret>tring</warning>(1));
    }
  }
}