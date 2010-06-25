// "Annotate parameter 's' as @NonNls" "true"

import org.jetbrains.annotations.NonNls;

class Foo {
  public void doTest() {
    doStringTest("test");
  }

  private void doStringTest(@NonNls String s) {
  }
}
