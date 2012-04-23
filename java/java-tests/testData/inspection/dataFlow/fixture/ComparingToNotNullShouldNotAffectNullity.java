import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class Bar3 {

  @NotNull
  Object getObj() {
    return new Object();
  }

  void foo(Collection<Object> collection) {
    if (!collection.isEmpty()) {
      Object first = collection.iterator().next();
      if (first != getObj() || collection.size() > 0) {
        System.out.println(first.hashCode());
      }
      if (first == getObj() || collection.size() > 0) {
        System.out.println(first.hashCode());
      }
      if (first == null) {
        System.out.println(<warning descr="Method invocation 'first.hashCode()' may produce 'java.lang.NullPointerException'">first.hashCode()</warning>);
      }
    }
  }
}