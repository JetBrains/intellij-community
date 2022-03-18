import java.util.function.*;

import typeUse.*;

class Test {
  public <T> void ok(final Supplier<@Nullable T> finder, final Consumer<@NotNull T> action) {
    final var found = finder.get();
    if (found != null) action.accept(found);
  }

  public <T> void nok(final Supplier<@Nullable T> finder) {
    ok(finder, (t) -> {});
  }

  public static void main(final String[] args) {
    final var test = new Test();

    test.nok(() -> null);
  }
}