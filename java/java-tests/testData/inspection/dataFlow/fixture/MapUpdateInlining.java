import foo.*;

import java.util.Map;

class MapUpdateInlining {
  void testKey(Map<String, String> map) {
    System.out.println(map.computeIfAbsent("foo",
                                           k -> <warning descr="Condition 'k.equals(\"blahblah\")' is always 'false'">k.equals("blahblah")</warning> ? "bar" : "baz").trim());
  }

  void testValue(Map<String, @NotNull String> map) {
    System.out.println(map.computeIfAbsent("foo", k -> null).<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>());
    System.out.println(map.computeIfAbsent("foo", k -> "bar").trim());
  }

  void testNullable(Map<String, @Nullable String> map) {
    System.out.println(map.computeIfAbsent("foo", k -> null).<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>());
    System.out.println(map.computeIfAbsent("foo", k -> "bar").trim());
  }

  void testPresent(Map<String, @Nullable String> map, String key) {
    System.out.println(map.computeIfPresent(key, (k, v) -> v+"xyz").<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>());
    String res1 = map.computeIfPresent("foo", (k, v) -> <warning descr="Condition 'v == null' is always 'false'">v == null</warning> ? "oops" : k);
    String res = map.computeIfPresent(key, (k, v) -> k.isEmpty() ? "foo" : "bar");
    if(<warning descr="Condition 'res != null && res.equals(\"x\")' is always 'false'">res != null && <warning descr="Condition 'res.equals(\"x\")' is always 'false' when reached">res.equals("x")</warning></warning>) {
      System.out.println("impossible");
    }
  }

  void testCompute(Map<String, @Nullable String> map, String key) {
    System.out.println(map.compute(key, (k, v) -> v+"xyz").trim());
    String res = map.compute("foo", (k, v) -> v == null ? "foo" : "bar");
    if(<warning descr="Condition 'res == null' is always 'false'">res == null</warning>) {
      System.out.println("impossible");
    }
    if(<warning descr="Condition 'res.equals(\"x\")' is always 'false'">res.equals("x")</warning>) {
      System.out.println("impossible");
    }
  }

  void testMerge(Map<String, String> map) {
    String res = map.merge("foo", "bar", (v1, v2) -> v1.equals("x") || <warning descr="Condition 'v2.equals(\"y\")' is always 'false' when reached">v2.equals("y")</warning> ? "oops" : "argh");
    if(res.equals("bar")) {
      System.out.println("possible");
    }
    if(res.equals("oops")) {
      System.out.println("possible");
    }
    if(res.equals("argh")) {
      System.out.println("possible");
    }
    if(<warning descr="Condition 'res.equals(\"qux\")' is always 'false'">res.equals("qux")</warning>) {
      System.out.println("impossible");
    }
  }

  void testMerge2(Map<String, @Nullable String> map, String key, @Nullable String val) {
    if(<warning descr="Condition 'map.merge(key, val, String::concat) == null' is always 'false'">map.merge(key, <warning descr="Argument 'val' might be null">val</warning>, String::concat) == null</warning>) {
      System.out.println("impossible");
    }
  }
}
