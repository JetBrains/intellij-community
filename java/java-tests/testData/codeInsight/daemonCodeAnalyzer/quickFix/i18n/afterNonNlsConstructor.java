// "Annotate variable 'a' as @NonNls" "true"

import org.jetbrains.annotations.NonNls;

class Foo {
  public void doTest() {
    @NonNls String a = new String("test");
  }
}
