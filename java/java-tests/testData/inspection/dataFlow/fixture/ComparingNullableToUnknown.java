import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

class Bar3 {

  public void foo(@Nullable Object element) {
    final String elementType = element != null ? element.toString() : null;
    if (elementType == someString()) {
      System.out.println(element.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
    }
  }

  String someString() { return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>; }
}