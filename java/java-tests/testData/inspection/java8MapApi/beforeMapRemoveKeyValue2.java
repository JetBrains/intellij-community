// "Replace with 'remove()' call" "true"
import java.util.Map;

public class Main {
  void test(Map<String, String> map, String key, String value) {
    if<caret>/*1*/ (/*2*/value.equals(/*3*/map.get(/*4*/key))) {
      map.remove(key);/*5*/
    }
  }
}
