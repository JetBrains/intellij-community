import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;
class Test {

  private void a()
  {
    b(newMethod());
  }

    private @NotNull Supplier newMethod() {
        return (s) -> {
            System.out.println(s);
        };
    }

    void b(Supplier s) {}
}