import org.jetbrains.annotations.NotNull;
import java.util.function.Supplier;

class TestTest {
  public void test() {
    @NotNull String string = reSupplier(this::supplyString);

    onlyNonNull(string);
  }

  public void onlyNonNull(@NotNull String string) {
  }

  public @NotNull String supplyString() {
    return "test";
  }

  public <T> T reSupplier(@NotNull Supplier<T> supplier) {
    return supplier.get();
  }
}