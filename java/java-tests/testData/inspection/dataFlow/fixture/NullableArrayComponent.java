import foo.*;

import java.util.List;

class TestCompilerWarnings {
  public void m(@NotNull Object x) {
    assert x != null;
  }

  public void test1Array(@Nullable String @NotNull [] x) {
    if (<warning descr="Condition 'x == null' is always 'false'">x == null</warning>) {
      System.out.println("x is null");
    }
    m(x);
    m(<warning descr="Argument 'x[0]' might be null">x[0]</warning>);
  }

  public void test2Array(@NotNull String @Nullable [] x) {
    if (x == null) {
      System.out.println("x is null");
    } else {
      m(x[0]);
    }
    m(<warning descr="Argument 'x' might be null">x</warning>);
  }

}