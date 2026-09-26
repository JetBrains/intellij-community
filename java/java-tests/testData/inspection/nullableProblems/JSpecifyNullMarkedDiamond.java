import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Container<T> {

  private final @Nullable T a;

  public Container(final @Nullable T a) {
    super();
    this.a = a;
  }

  static Container<String> ofString1(final @Nullable String a) {
    return new Container<>(a);
  }

  static Container<String> ofString2(final @Nullable String a) {
    return new Container<String>(a);
  }
}