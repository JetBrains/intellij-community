// "Replace 'switch' with 'if'" "true"
abstract class Test {
  abstract Object getObject();

  void foo() {
      Class<?> i = getObject().getClass();
      if (RuntimeException.class.equals(i)) {
      } else if (IOException.class.equals(i)) {
      }
  }
}