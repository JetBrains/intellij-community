// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(Object e) {
    <caret>switch(e.getClass()) {
      case RuntimeException.class:
        break;
      case IOException.class:
        break;
    }
  }
}