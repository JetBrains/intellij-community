import org.jetbrains.annotations.*;

class Inspection {

  @Nullable
  private Object o;

  public void test1() {
    check(o);
    foo(<warning descr="Argument 'o' might be null">o</warning>);
  }

  public static Object check(@Nullable final Object o) {
    if (o != null && is()) {
    }
    return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
  }

  private static boolean is() {
    System.out.println();
    return true;
  }

  private static void foo(@NotNull final Object obj) {
  }
}