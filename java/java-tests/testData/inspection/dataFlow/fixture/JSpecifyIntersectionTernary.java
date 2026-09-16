import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Test {
  interface A {}
  interface B {}

  static native <T extends A & B> T notNull();

  static native <T extends @Nullable A & @Nullable B> @Nullable T nullable();

  void test(boolean flag) {
    // The least upper bound of the branches is nullable in both orders
    var notNullFirst = flag ? notNull() : nullable();
    var nullableFirst = flag ? nullable() : notNull();
  }
}
