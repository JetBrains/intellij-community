import java.util.function.Supplier;
class Test {

  private void a()
  {
    b((s) -> {
      System.out.println(<selection>s</selection>);
    });
  }

  void b(Supplier s) {}
}