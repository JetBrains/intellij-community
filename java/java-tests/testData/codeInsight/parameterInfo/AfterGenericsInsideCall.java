import java.util.*;

class First {
  public static <T> List<T> first(final Class<T> type, boolean tags) {
    return null;
  }

  public static <T> List<T> first(final Class<T> type) {
    return first(type, <caret>true);
  }
}