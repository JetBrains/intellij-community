import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface NonnullInterface {
  @NotNull
  Object nonNullMethod();
}

class P2 {

  private void callTest() {
    test(new NonnullInterface() {
      <warning descr="Method annotated with @Nullable must not override @NotNull method">@Nullable</warning>
      @Override
      public String nonNullMethod() {
        return null;
      }
    });
    test(this::<warning descr="Method annotated with @Nullable must not override @NotNull method">getNull</warning>);
    test(this::getPrimitive);
  }

  @Nullable
  String getNull() { return null; }

  String getNonAnnotated() { return null; }

  boolean getPrimitive() { return true; }

  private void test(final NonnullInterface function) { }
}
