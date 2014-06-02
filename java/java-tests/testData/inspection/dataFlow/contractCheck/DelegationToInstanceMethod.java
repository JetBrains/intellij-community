import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("!null,true->!null")
  String delegationToInstance(@NotNull Foo f, boolean createIfNeeded) { return f.getString(createIfNeeded); }

  @Contract("true->!null")
  String getString(boolean createIfNeeded) { return createIfNeeded ? "" : null; }

}
