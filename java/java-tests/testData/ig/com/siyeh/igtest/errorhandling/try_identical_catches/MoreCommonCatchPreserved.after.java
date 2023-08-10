import java.io.IOException;

class C {
  void foo() {
    try {
      bar();
    } catch (RuntimeException e) {
      System.out.println("1");
      throw e;
    } catch (Throwable throwable) {
      System.out.println("Got exception");
      throwable.printStackTrace();
    }
  }

  void bar() throws IOException{}
}