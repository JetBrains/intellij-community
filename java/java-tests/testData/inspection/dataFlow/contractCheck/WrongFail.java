import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("_,null->fail")
  boolean bar(int i, @Nullable Object foo) {
    return <warning descr="Contract clause '_, null -> fail' is violated">foo == null</warning>;
  }

}
