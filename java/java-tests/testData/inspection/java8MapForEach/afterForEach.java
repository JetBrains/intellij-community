// "Fix all 'Replace with Map.forEach' problems in file" "true"
import java.util.Map;
import java.util.function.Supplier;

public class Test {
  public static void testInline(Map<String, Integer> map) {
    map.forEach((key, value) -> System.out.println(key + ":" + value));
  }

  public static void testKey(Map<String, Integer> map) {
    map.forEach((str, value) -> System.out.println(str + ":" + value));
  }

  public static void testValue(Map<String, Integer> map) {
    map.forEach((str, num) -> System.out.println(str + ":" + num));
  }

  public static void testTwoVarsWildcard(Map<? extends String, Integer> map) {
    map.forEach((str, num) -> {
        System.out.println(str + ":" + num);
        String str2 = str;
        System.out.println(str2);
    });
  }

  public static <T extends Map<?, ?>> void testGeneric(Supplier<T> map) {
    map.get().forEach((key, value) -> System.out.println(key + ":" + value));
  }

  public static <T extends Map<?, ?>> void testUsedHashCode(Supplier<T> map) {
    map.get().entrySet().forEach(e -> System.out.println(e.getKey()+":"+e.getValue()+":"+e.hashCode()));
  }

  public static void testForLoop(Map<String, Integer> map) {
      map.forEach((key, value) -> System.out.println(key + ":" + value));
  }

  public static void testForLoop2(Map<String, Integer> map) {
      map.forEach((str, num) -> System.out.println(str + ":" + num));
  }

  public static void testForLoop3(Map<String, Integer> map) {
      map.forEach((str, num) -> {
          System.out.println(str + ":" + num);
          System.out.println(num + ":" + str);
      });
  }

  public static void testForLoopSet(Map<String, Integer> map) {
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      String str = entry.getKey();
      Integer num = entry.getValue();
      System.out.println(str + ":" + entry.getValue());
      entry.setValue(1);
    }
  }

  public static void testForLoopPrimitive(Map<String, Integer> map) {
      map.forEach((str, value) -> {
          int num = value;
          System.out.println(str + ":" + num);
      });
  }

  public static void testForLoopSideEffect(Map<String, Integer> map) {
    Integer num;
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      String str = entry.getKey();
      num = entry.getValue();
      System.out.println(str + ":" + num);
    }
  }

  public static void testForLoopThrow(Map<String, Integer> map) throws Exception {
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      String str = entry.getKey();
      Integer num = entry.getValue();
      if(num > 0) throw new Exception();
      System.out.println(str + ":" + num);
    }
  }

  public static void testForLoopThrowRuntime(Map<String, Integer> map) throws Exception {
      map.forEach((str, num) -> {
          if (num > 0) throw new RuntimeException();
          System.out.println(str + ":" + num);
      });
  }

  void forEach(Map<String, String> map) {
      map.forEach((key, value) -> {
          //long comment
      });
  }
}
