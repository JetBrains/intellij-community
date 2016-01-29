import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class Bar3 {

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
      if (<warning descr="Condition 'first == null' is always 'false'">first == null</warning>) {
        System.out.println(first.hashCode());
      }
    }
  }

  void foo2(Collection<Object> collection) {
    if (!collection.isEmpty()) {
      Object first = collection.iterator().next();
      if (first != getObj() || collection.size() > 0) {
        first.hashCode();
      }
    }
  }

  void foo3(Collection<Object> collection) {
    if (!collection.isEmpty()) {
      Object first = collection.iterator().next();
      if (first == getObj() || collection.size() > 2) {
        System.out.println(first.hashCode());
      }
      if (first == null) {
        System.out.println(first.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
      }
    }
  }
}