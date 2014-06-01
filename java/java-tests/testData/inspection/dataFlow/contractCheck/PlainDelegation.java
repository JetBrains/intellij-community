import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("null->false")
  boolean plainDelegation(Object x) {
    return <warning descr="Contract clause 'null -> false' is violated">bar(2, x)</warning>;
  }

  @Contract("_,null->true")
  boolean bar(int i, @Nullable Object foo) {
    return foo == null;
  }

}
