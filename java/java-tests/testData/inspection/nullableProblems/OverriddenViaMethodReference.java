import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;

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
    test(this::<warning descr="Not annotated method is used as an override for a method annotated with NotNull">getNonAnnotated</warning>);

    test(WithImplicitConstructor::new);
    test(WithExplicitConstructor::new);
  }

  @Nullable
  String getNull() { return null; }

  String getNonAnnotated() { return null; }

  boolean getPrimitive() { return true; }

  private void test(final NonnullInterface function) { }

  private Iterable<String> arrayToIterable(String... array) {
    return Arrays.stream(array)::iterator;
  }
}

class WithImplicitConstructor {}
class WithExplicitConstructor { WithExplicitConstructor() {} }