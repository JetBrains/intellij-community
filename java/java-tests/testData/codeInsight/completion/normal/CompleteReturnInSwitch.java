
class Main {

  private static interface Empty {
    void f();
  }

  private Empty f(Object o) {
    return switch (o) {
      default -> () -> {
        retu<caret>
      };
    };
  }

  private Empty g(Object o1, Object o2) {
    return switch (o1) {
      default -> () -> {
        switch (o2) {
          case null -> { retu<caret> }
          default -> { retu<caret> }
        }
      };
    };
  }
}