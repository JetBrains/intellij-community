import java.util.Map;

class Test {
  void f() {}
  void m(Map<String, ? extends Test> map, String name) {
    map.get(name).f();
  }
}