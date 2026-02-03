import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("null->true")
  boolean bar(@Nullable Object foo) {
    return <warning descr="Contract clause 'null -> true' is violated">foo != null && foo.hashCode() == 3</warning>;
  }

}
