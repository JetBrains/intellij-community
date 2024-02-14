// "Extract common part removing branch" "true"

class EmptyStatements {

  private void refactor() {
    if<caret> (new java.util.Random().nextBoolean()) {
      foo();
      ;;bar();;;;
    } else {
      bar();;
    }
  }

  private void foo() {}

  private void bar() {}
}