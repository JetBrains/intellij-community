import java.util.function.Supplier;
class Test {

  private void a()
  {
    b((s) -> {
      System.out.println(newMethod((Object) s));
    });
  }

    private boolean newMethod(Object s) {
        return s;
    }

    void b(Supplier s) {}
}