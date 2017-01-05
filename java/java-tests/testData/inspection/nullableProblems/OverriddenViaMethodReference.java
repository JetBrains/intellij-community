import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface NonnullInterface {
  @NotNull
  String nonNullMethod();
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
    test(this::<warning descr="Not annotated method is used as an override for a method annotated with NotNull">getNonAnnotated</warning>);
  }

  @Nullable
  String getNull() { return null; }

  String getNonAnnotated() { return null; }

  private void test(final NonnullInterface function) { }
}
