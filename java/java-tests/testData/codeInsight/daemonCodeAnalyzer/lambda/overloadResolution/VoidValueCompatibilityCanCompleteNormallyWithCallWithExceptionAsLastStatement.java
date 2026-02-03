import java.io.IOException;

class Test {
  {
    query(() -> {
      process();
    });
  }

  public static void process() throws IOException {}

  private <T> void <warning descr="Private method 'query(Test.B<T>)' is never used">query</warning>(B<T> var2) {
    System.out.println(var2);

  }

  private void query( A rch)  {
    System.out.println(rch);
  }

  interface A {
    void m() throws IOException;
  }

  interface B<T> {
    T n() throws IOException;
  }
}