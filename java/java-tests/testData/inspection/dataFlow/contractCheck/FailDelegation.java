import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("!null,true->!null")
  String delegationToInstance(@NotNull Foo f, boolean createIfNeeded) {
    return <warning descr="Contract clause '!null, true -> !null' is violated: exception might be thrown instead of returning !null">f.getString(createIfNeeded)</warning>;
  }

  @Contract("true->fail")
  String getString(boolean fail) {
    if (fail) throw new RuntimeException();
    return "a";
  }

}
