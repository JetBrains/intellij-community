import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

class Bar3 {

  public void foo(@Nullable Object element) {
    final String elementType = element != null ? element.toString() : null;
    if (elementType == nullableString()) {
      System.out.println(<warning descr="Method invocation 'element.hashCode()' may produce 'java.lang.NullPointerException'">element.hashCode()</warning>);
    }
  }

  @Nullable String nullableString() { return null; }
}