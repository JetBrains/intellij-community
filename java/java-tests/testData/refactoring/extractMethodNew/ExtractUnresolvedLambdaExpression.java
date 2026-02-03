import java.util.function.Supplier;
class Test {

  private void a()
  {
    b(<selection>(s) -> {
      System.out.println(s);
    }</selection>);
  }

  void b(Supplier s) {}
}