// "Create class 'Baz'" "true"
import org.jetbrains.annotations.NotNull;

public class TestClass {

  public static void foo() {
    final @NotNull String bar = "bar";
    var baz = new Baz(bar);
  }

}

public class Baz {
    public Baz(@NotNull String bar) {
    }
}