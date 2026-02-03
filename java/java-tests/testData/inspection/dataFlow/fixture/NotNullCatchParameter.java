class MyException extends Exception {}

class Foo {
  public static void main(String[] args) {
    try {
      bar();
    } catch (Exception e) {
      if (e instanceof MyException) {
        return;
      }
      if (<warning descr="Condition 'e instanceof RuntimeException' is always 'true'">e instanceof RuntimeException</warning>) {
        return;
      }
      e.printStackTrace();
    }
  }

  private static void bar() throws MyException {
  }

}