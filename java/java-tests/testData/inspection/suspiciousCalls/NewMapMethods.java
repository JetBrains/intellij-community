
import java.util.Map;
import java.util.HashMap;

class Test {
  void m(Map<String, String> map, HashMap<String, String> hMap){
    map.getOrDefault(<warning descr="'Map<String, String>' may not contain keys of type 'Integer'">1</warning>, "");
    hMap.getOrDefault(<warning descr="'HashMap<String, String>' may not contain keys of type 'Integer'">1</warning>, "");

    map.getOrDefault("", "");
    hMap.getOrDefault("", "");

    map.remove(<warning descr="'Map<String, String>' may not contain keys of type 'Integer'">1</warning>, "");
    hMap.remove(<warning descr="'HashMap<String, String>' may not contain keys of type 'Integer'">1</warning>, "");
    map.remove("", "");
    hMap.remove("", "");
  }
}