public class BrokenAlignment {

  public static void main(String[] args) {

    Throwable error = null;
    try {
      doSomething();
    } catch (AssertionError e) {
      // rethrow error
      throw e;
    } catch (Throwable e) {
      // remember error
      error = e;
    }

    if (error != null) { // <<--- inspection warning
      // handle error ...
    }

  }

  public static void doSomething() {
    throw new RuntimeException("dummy");
  }

}