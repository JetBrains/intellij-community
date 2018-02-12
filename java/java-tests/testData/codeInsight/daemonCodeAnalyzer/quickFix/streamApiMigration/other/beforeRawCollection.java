// "Replace with forEach" "false"
import java.util.*;
import java.util.stream.Collectors

class A {
  public static <T> List<T> toTypedList1(Collection collection, Class<T> aClass) {
    if (collection.size() == 0) {
      return Collections.emptyList();
    }

    List<T> result = new ArrayList<>(collection.size());

    for (Object obj : coll<caret>ection) {
      result.add(aClass.cast(obj));
    }

    return result;
  }



}