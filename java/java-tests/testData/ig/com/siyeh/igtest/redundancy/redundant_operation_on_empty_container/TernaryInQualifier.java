import java.util.*;

final class Test {

  public static void main(String[] args) {
    List<Integer> keys = Arrays.asList(1, 2, 3, 4, 5);

    Map<Integer, Boolean> map1 = new HashMap<>();
    Map<Integer, Boolean> map2 = new HashMap<>();

    for (Integer key : keys) {
      (key % 2 == 0 ? map1 : map2).compute(key, (k, v) -> true);
    }

    map1.forEach((k, v) -> System.out.println(k + ", " + v));
    map2.forEach((k, v) -> System.out.println(k + ", " + v));
  }
}