public class Test {
  public void f(Object o ) {
    if (o instanceof String) {
      Runnable r = () -> {
        ((String) o).length()<caret>
      };
    }
  }

}
