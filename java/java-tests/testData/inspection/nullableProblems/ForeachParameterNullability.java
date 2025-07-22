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
    for (<warning descr="Parameter can be null">@NotNull</warning> Object o : getNullableStuff()) {
      System.out.println(o.getClass());
    }
    for (<warning descr="Parameter is always non-null">@Nullable</warning> Object o : getNotNullStuff()) {
      System.out.println(o.getClass());
    }
    getNullableStuff().forEach((<warning descr="Parameter can be null">@NotNull</warning> Object s) -> System.out.println(s.hashCode()));
    getNotNullStuff().forEach((<warning descr="Parameter is always non-null">@Nullable</warning> Object s) -> System.out.println(s.hashCode()));
  }
}