import java.io.IOException;

class C {
  void foo() {
    try {
      bar();
    } catch (IOException e) {
      System.out.println("Got exception");
      e.printStackTrace();
    } catch (RuntimeException e) {
      System.out.println("1");
      throw e;
    } <warning descr="'catch' branch identical to 'IOException' branch">catch (<caret>Throwable throwable)</warning> {
      System.out.println("Got exception");
      throwable.printStackTrace();
    }
  }

  void bar() throws IOException{}
}