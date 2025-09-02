import org.jspecify.annotations.*;

@NullMarked
class Test2 {
  void call(Test<? extends Base, ? extends @Nullable Base> a) {}

}
class Base {}

@NullMarked
class Test<T, E extends @Nullable Object> {}