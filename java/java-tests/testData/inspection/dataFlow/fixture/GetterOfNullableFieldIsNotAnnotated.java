import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class A {
  @Nullable
  private final String xxxx = "";

  public String getXxxx() {
    return xxxx;
  }
}

class B {

  @NotNull
  String x = <warning descr="Expression 'new A().getXxxx()' might evaluate to null but is assigned to a non-null variable">new A().getXxxx()</warning>;
}
