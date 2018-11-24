import typeUse.*;
import java.util.*;

class JC {

  public static Collection<@Nullable Object> getNullableStuff() {
    return Collections.emptyList();
  }
  public static Collection<@NotNull Object> getNotNullStuff() {
    return Collections.emptyList();
  }

  void usage() {
    for (<warning descr="Loop parameter can be null">@NotNull</warning> Object o : getNullableStuff()) {
      System.out.println(o.getClass());
    }
    for (<warning descr="Loop parameter is always not-null">@Nullable</warning> Object o : getNotNullStuff()) {
      System.out.println(o.getClass());
    }
  }
}