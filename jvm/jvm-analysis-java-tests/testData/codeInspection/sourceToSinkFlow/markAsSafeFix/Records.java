import org.checkerframework.checker.tainting.qual.Untainted;

record Foo(String s) {

  @Untainted String doSmth() {
    return s<caret>;
  }
}