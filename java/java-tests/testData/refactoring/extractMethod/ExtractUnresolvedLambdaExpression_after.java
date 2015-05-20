import java.util.function.Supplier;
class Test {

  private void a()
  {
    b(newMethod());
  }

    private Supplier newMethod() {
        return (s) -> {
          System.out.println(s);
        };
    }

    void b(Supplier s) {}
}