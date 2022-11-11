// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo() {
      Class<?> aClass = getObject().getClass();
      if (aClass.equals(RuntimeException.class)) {
      } else if (aClass.equals(IOException.class)) {
      }
  }
}