import org.jetbrains.annotations.NonNls;

// "Annotate method 'test' as @NonNls" "true"
class Foo {
  @NonNls
  Foo test(String s) {
    return this;
  }
  
  public boolean doTest() {
    System.out.println((test("Hello")).toString());
  }
}
