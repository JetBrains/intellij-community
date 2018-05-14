import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("null,_->true")
  boolean bar(@Nullable Object foo, int i) {
    if (foo == null) {
      <warning descr="Return value of clause 'null, _ -> true' could be replaced with 'fail' as method always fails in this case">throw new RuntimeException();</warning>
    }
    return i == 2;
  }

}
