import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map<String, String> sortMap = null;
    for (String s : sortMap.val<caret>ues()) {
      System.out.println("Value is: " + s);
      CharSequence val = s;
      System.out.println(val);
    }
  }
}
