public class Foo {

  private LocalDate dateField;

  void foo(LocalDate date) {

  }

  void bar() {
    foo(<caret>)
  }
}

class LocalDate {
  public static final LocalDate MAX;
  public static final LocalDate MIN;
}