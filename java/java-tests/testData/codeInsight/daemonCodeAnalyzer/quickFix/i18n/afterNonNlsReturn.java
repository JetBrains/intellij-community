import org.jetbrains.annotations.NonNls;

// "Annotate method 'doTest()' as '@NonNls'" "true-preview"
class Foo {
  public @NonNls String doTest() {
    return "text";
  }
}
