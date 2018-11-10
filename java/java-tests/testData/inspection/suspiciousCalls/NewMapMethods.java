
import java.util.Map;

class Test {
  void m(Map<String, String> map){
    map.getOrDefault(<warning descr="'Map<String, String>' may not contain keys of type 'Integer'">1</warning>, "");
    map.getOrDefault("", "");
    map.remove(<warning descr="'Map<String, String>' may not contain keys of type 'Integer'">1</warning>, "");
    map.remove("", "");
  }
}