import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map<String, String> sortMap = null;
    for (Map.Entry<String, String> entry : sortMap.entry<caret>Set()) {
        String key = entry.getKey();
        System.out.println("Key is: " + key);
      System.out.println("Value is: " + entry.getValue());
      String val = entry.getValue();
      System.out.println(key+"="+val);
    }
  }
}
