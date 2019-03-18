// "Replace with 'replaceAll' method call" "false"

public class Main {
  public void test() {
    Map<String, String> map = new HashMap<>();
    map.put("foo", "bar");
    String s = "foo";
    s += "bar";
    for<caret> (String k : map.keySet()) {
      map.put(k, s);
    }
  }
}