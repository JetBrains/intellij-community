import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("null,_->true")
  boolean bar(@Nullable Object foo, int i) {
    if (foo == null) {
      <warning descr="Contract clause 'null, _ -> true' is violated: exception might be thrown instead of returning true">throw new RuntimeException();</warning>
    }
    return i == 2;
  }

}
