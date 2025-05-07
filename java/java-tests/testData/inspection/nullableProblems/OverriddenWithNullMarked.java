import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

class Main {
  @NullMarked
  interface Api {
    @Nullable
    <T> T call();
  }

  @NullMarked
  static class Impl implements Api {
    @Nullable
    public <T> T call() {
      return null;
    }
  }
}