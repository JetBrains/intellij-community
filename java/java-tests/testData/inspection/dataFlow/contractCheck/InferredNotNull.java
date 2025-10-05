import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

class Foo {
  @Contract("<warning descr="Parameter 'foo' is inferred to be non-null, so 'null' is not applicable">null</warning> -> true")
  public boolean test(Boolean foo) {
    return foo.booleanValue();
  }
}
