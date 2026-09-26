class App {
  static void f(Runnable r) {
    r.run();
  }

  public static void main(String[] args) {
    RuntimeException[] exception = {null};
    try {
      f(() -> {
        exception[0] = new RuntimeException();
        throw exception[0];
      });
    } catch (RuntimeException e) {
      System.out.println(e == exception[0]); // was false-positive
    }
  }
}