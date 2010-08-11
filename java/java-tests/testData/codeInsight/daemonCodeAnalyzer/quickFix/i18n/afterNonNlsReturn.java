import org.jetbrains.annotations.NonNls;

// "Annotate method 'doTest' as @NonNls" "true"
class Foo {
  @NonNls
  public String doTest() {
    return "text";
  }
}
