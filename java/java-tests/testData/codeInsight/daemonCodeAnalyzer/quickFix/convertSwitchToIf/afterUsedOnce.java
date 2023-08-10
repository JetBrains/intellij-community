// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo() {
      if (getObject().getClass().equals(RuntimeException.class)) {
          System.out.println("RuntimeException");
      } else {
          System.out.println("Other");
      }
  }
}