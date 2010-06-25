// "Annotate method 'doTest' as @NonNls" "true"

import org.jetbrains.annotations.NonNls;

class Foo {
  @NonNls
  public String doTest() {
    return "text";
  }
}
