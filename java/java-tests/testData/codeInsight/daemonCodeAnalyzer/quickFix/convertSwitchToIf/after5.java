// "Replace 'switch' with 'if'" "true"
abstract class Test {
  abstract Object getObject();

  void foo() {
      Class<?> i = getObject().getClass();
      if (i.equals(RuntimeException.class)) {
      } else if (i.equals(IOException.class)) {
      }
  }
}