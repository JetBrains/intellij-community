import org.checkerframework.checker.tainting.qual.Untainted;

record Foo(@Untainted String s) {

  @Untainted String doSmth() {
    return s<caret>;
  }
}