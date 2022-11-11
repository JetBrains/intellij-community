// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo() {
    <caret>switch(getObject().getClass()) {
      case RuntimeException.class:
        break;
      case IOException.class:
        break;
    }
  }
}