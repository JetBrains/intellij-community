import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map<String, String> sortMap = null;
    for (String val : sortMap.val<caret>ues()) {
      System.out.println("Value is: " + val);
        System.out.println(val);
    }
  }
}
