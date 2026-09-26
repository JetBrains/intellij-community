import java.io.IOException;

class C {
  void foo() {
    try {
      bar();
    }
    catch (IOException e) {}
    catch (RuntimeException e) {
      System.out.println("1");
      throw e;
    }
    <warning descr="'catch' branch identical to 'IOException' branch">catch (<caret>Exception exception)</warning> {}
    catch (Throwable throwable) {
      System.out.println("1");
    }
  }

  void bar() throws IOException{}
}