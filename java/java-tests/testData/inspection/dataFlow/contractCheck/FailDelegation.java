import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("!null,true->!null")
  String delegationToInstance(@NotNull Foo f, boolean createIfNeeded) {
    return f.getString(createIfNeeded); // not smart enough to check this 
  }

  @Contract("true->fail")
  String getString(boolean fail) {
    if (fail) throw new RuntimeException();
    return "a";
  }

}
