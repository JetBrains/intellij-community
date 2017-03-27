// "Fix all 'Replace with Map.forEach' problems in file" "true"
import java.util.Map;
import java.util.function.Supplier;

public class Test {
  public static void testInline(Map<String, Integer> map) {
    map.entrySet().for<caret>Each(entry ->
                             System.out.println(entry.getKey() +":"+entry.getValue())
    );
  }

  public static void testKey(Map<String, Integer> map) {
    map.entrySet().forEach(entry -> {
      String str = entry.getKey();
      System.out.println(str +":"+entry.getValue());
    });
  }

  public static void testValue(Map<String, Integer> map) {
    map.entrySet().forEach(entry -> {
      String str = entry.getKey();
      Integer num = entry.getValue();
      System.out.println(str +":"+ num);
    });
  }

  public static void testTwoVarsWildcard(Map<? extends String, Integer> map) {
    map.entrySet().forEach(entry -> {
      String str = entry.getKey();
      Integer num = entry.getValue();
      System.out.println(str +":"+ num);
      String str2 = entry.getKey();
      System.out.println(str2);
    });
  }

  public static <T extends Map<?, ?>> void testGeneric(Supplier<T> map) {
    map.get().entrySet().forEach(e -> System.out.println(e.getKey()+":"+e.getValue()));
  }

  public static <T extends Map<?, ?>> void testUsedHashCode(Supplier<T> map) {
    map.get().entrySet().forEach(e -> System.out.println(e.getKey()+":"+e.getValue()+":"+e.hashCode()));
  }

  public static void testForLoop(Map<String, Integer> map) {
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      System.out.println(entry.getKey() + ":" + entry.getValue());
    }
  }

  public static void testForLoop2(Map<String, Integer> map) {
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      String str = entry.getKey();
      Integer num = entry.getValue();
      System.out.println(str + ":" + num);
    }
  }

  public static void testForLoop3(Map<String, Integer> map) {
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      String str = entry.getKey();
      Integer num = entry.getValue();
      System.out.println(str + ":" + entry.getValue());
      System.out.println(num + ":" + str);
    }
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
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      String str = entry.getKey();
      int num = entry.getValue();
      System.out.println(str + ":" + num);
    }
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
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      String str = entry.getKey();
      Integer num = entry.getValue();
      if(num > 0) throw new RuntimeException();
      System.out.println(str + ":" + num);
    }
  }

  void forEach(Map<String, String> map) {
    for (Map.Entry<String, String> entry : map.entrySet()) {
      //long comment
    }
  }
}
