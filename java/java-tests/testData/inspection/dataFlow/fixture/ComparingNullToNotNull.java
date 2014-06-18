import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

class Bar3 {

  public void foo(@Nullable Object element) {
    final String elementType = element != null ? element.toString() : null;
    if (elementType == "") {
      System.out.println(element.hashCode());
    }
  }
}