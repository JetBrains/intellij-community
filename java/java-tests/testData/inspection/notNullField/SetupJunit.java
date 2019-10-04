import org.jetbrains.annotations.NotNull;

public class SetupJunit extends junit.framework.TestCase {
  @NotNull String foo;
  <warning descr="Not-null fields must be initialized">@NotNull</warning> String bar;

  public void setUp() {
    foo = "foo";
  }

  public void notSetUp() {
    bar = "bar";
  }
}