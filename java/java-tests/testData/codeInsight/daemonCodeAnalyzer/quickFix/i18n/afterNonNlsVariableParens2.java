import org.jetbrains.annotations.NonNls;

// "Annotate variable 'a' as @NonNls" "true"
class Foo {
  public void doTest() {
    @NonNls String a = (this.s("test"));
  }
  
  String s(String s) {
    return s;
  }
}
