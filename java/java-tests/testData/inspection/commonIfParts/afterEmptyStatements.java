// "Extract common part removing branch" "true"

class EmptyStatements {

  private void refactor() {
    if (new java.util.Random().nextBoolean()) {
      foo();
    }
      bar();
  }

  private void foo() {}

  private void bar() {}
}