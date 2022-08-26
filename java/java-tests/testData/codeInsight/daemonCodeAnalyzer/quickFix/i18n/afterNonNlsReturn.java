import org.jetbrains.annotations.NonNls;

// "Annotate method 'doTest()' as '@NonNls'" "true-preview"
class Foo {
  @NonNls
  public String doTest() {
    return "text";
  }
}
