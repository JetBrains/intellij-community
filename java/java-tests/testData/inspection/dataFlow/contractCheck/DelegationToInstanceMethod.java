import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("<warning descr="Parameter 'f' is annotated as non-null, so '!null' is always satisfied">!null</warning>,true->!null")
  String delegationToInstance(@NotNull Foo f, boolean createIfNeeded) { return f.getString(createIfNeeded); }

  @Contract("true->!null")
  String getString(boolean createIfNeeded) { return createIfNeeded ? "" : null; }

}
