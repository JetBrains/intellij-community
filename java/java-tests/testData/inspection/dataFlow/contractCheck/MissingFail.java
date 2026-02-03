import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Foo {

  @Contract("true->fail")
  void <warning descr="Contract clause 'true -> fail' is violated: no exception is thrown">assertFalse</warning>(boolean fail) {
  }

}
