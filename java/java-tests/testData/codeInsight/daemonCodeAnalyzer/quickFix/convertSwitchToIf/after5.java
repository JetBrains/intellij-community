// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(Object e) {
      Class<?> i = e.getClass();
      if (i.equals(RuntimeException.class)) {
      } else if (i.equals(IOException.class)) {
      }
  }
}