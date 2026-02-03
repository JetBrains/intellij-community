import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class Foo {
  abstract String getString();

  @Contract("null -> null;!null -> !null")
  public static String delegate(@Nullable String s) {
    return s == null ? null : s.substring(1);
  }

  @Contract("null -> null;!null -> !null")
  public static String callee(@Nullable Foo element) {
    return element == null ? null : delegate(element.getString());
  }


  @Contract("!null -> !null")
  @Nullable public static String delegate2(@Nullable String s) {
    return s == null ? null : s.substring(1);
  }

  @Contract("!null -> !null")
  @Nullable public static String callee2(@Nullable Object element) {
    if (element instanceof Foo) return delegate2(((Foo)element).getString());
    if (element != null) return element.toString();
    return null;
  }


}
