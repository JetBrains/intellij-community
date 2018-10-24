// "Replace 'switch' with 'if'" "true"
abstract class Test {
  abstract Object getObject();

  void foo() {
    <caret>switch(getObject().getClass()) {
      case RuntimeException.class:
        System.out.println("RuntimeException");
        break;
      default:
        System.out.println("Other");
        break;
    }
  }
}