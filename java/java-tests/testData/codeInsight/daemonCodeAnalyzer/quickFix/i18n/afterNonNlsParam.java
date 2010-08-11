import org.jetbrains.annotations.NonNls;

// "Annotate parameter 's' as @NonNls" "true"
class Foo {
  public void doTest() {
    doStringTest("test");
  }

  private void doStringTest(@NonNls String s) {
  }
}
