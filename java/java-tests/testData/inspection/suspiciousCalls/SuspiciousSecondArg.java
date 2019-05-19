import java.util.*;
class MyTest {
  {
    Map<String, String> map = new HashMap<>();
    map.remove("key", <warning descr="'Map<String, String>' may not contain values of type 'Integer'">42</warning>);
  }
}