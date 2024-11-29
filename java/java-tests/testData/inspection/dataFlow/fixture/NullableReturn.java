import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

class NullableReturn {
  @NotNull Object test(Object o, Object o2, Object o3) {
    Object x = o == null ? o3 : o2;
    // probably ephemeral (e.g. o3 could be never null; o2 only), but it's ok to warn in the absence of nullity annotations
    return x == null ? <warning descr="Expression 'o3' might evaluate to null but is returned by the method declared as @NotNull">o3</warning> : x;
  }

  interface Context {}

  static class Foo {
    @Nullable
    protected Boolean executeImpl(@Nullable Context context) {
      return null;
    }
  }

  static class Bar extends Foo {
    @NotNull
    @Contract(pure = true)
    @Override
    protected Boolean executeImpl(@Nullable Context context) {
      return <warning descr="Expression 'super.executeImpl(context)' might evaluate to null but is returned by the method declared as @NotNull">super.executeImpl(context)</warning>;
    }
  }
}