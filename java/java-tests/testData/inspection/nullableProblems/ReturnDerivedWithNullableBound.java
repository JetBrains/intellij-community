import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class ReturnDerivedWithNullableBound {
  Bar<String> getNonNullBar() {
    return new Bar<>();
  }

  Foo<String> getNonNullFoo() {
    return getNonNullBar();
  }

  static class Foo<T extends @Nullable Object> {
  }

  static class Bar<T extends @Nullable Object> extends Foo<T> {
  }
}
