import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class A {
  @Nullable
  private final String xxxx = "";

  @NotNull
  public String getXxxx() {
    return xxxx;
  }
}

class B {

  @NotNull
  String x = new A().getXxxx();
}
