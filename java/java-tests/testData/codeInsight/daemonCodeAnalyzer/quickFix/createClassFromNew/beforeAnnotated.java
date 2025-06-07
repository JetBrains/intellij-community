// "Create class 'Baz'" "true"
import org.jetbrains.annotations.NotNull;

public class TestClass {

  public static void foo() {
    final @NotNull String bar = "bar";
    var baz = new <caret>Baz(bar);
  }

}