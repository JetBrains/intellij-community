import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("!null,true->!null")
  String delegationToInstance(@NotNull Foo f, boolean createIfNeeded) {
    return <warning descr="Return value of clause '!null, true -> !null' could be replaced with 'fail' as method always fails in this case">f.getString(createIfNeeded)</warning>;
  }

  @Contract("!null,false->!null")
  String delegationToInstanceOk(@NotNull Foo f, boolean createIfNeeded) {
    return f.getString(createIfNeeded);
  }

  @Contract("true->fail")
  String getString(boolean fail) {
    if (fail) throw new RuntimeException();
    return "a";
  }

}
