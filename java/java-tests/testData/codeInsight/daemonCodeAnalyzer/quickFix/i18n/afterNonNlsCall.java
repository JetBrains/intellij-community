// "Annotate variable 'a' as @NonNls" "true"

import org.jetbrains.annotations.NonNls;

class Foo {
  public boolean doTest() {
    @NonNls String a;
    return a.startsWith("te<caret>st");
  }
}
