// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "false"

import java.util.*;
import org.jetbrains.annotations.Contract;

class Main {

  @Contract(pure = true)
  public static <T> List<T> unmodifiableOrEmptyList(List<? extends T> original) {
    int size = original.size();
    if (size == 0) {
      return Collections.emptyList();
    }
    return Collections.<caret>unmodifiableList(original);
  }
}
