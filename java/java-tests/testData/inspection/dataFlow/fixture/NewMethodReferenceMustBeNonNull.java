import org.jetbrains.annotations.NotNull;
import java.util.function.Function;
import java.util.function.Supplier;

class Main {
  public static void main(String[] args) {
    System.out.println("Hello, World!");

    useConstructorAsFunction(InnerClass::new);
    useConstructorAsSupplier(InnerClass::new);
  }

  public static void useConstructorAsFunction(@NotNull Function<@NotNull String, @NotNull InnerClass> function) {

  }

  public static void useConstructorAsSupplier(@NotNull Supplier<@NotNull InnerClass> supplier) {

  }

  public static class InnerClass {
    public InnerClass(@NotNull String name) {
    }

    public InnerClass() {
    }
  }
}