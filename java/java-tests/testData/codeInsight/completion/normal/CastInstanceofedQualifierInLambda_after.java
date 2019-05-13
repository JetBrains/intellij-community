public class Test {
  public void f(Object o ) {
    Runnable r = () -> {
      if (o instanceof String) {
        ((String) o).length()<caret>
      }
    };
  }

}
