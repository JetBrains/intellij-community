import java.util.*;
import java.util.function.*;

class MethodReferenceWithArguments {

  static <T, U> T createWith(Function<? super U, ? extends T> methodRef, U arg) {
    return methodRef.apply(arg);
  }

  public static void main(String[] args) {

    Map<String, String> map = createWith(
      TreeMap::new,
      Comparator.<String>reverseOrder());

    map.put("aaa", "ONE");
    map.put("zzz", "TWO");

    System.out.println(map);
  }
}