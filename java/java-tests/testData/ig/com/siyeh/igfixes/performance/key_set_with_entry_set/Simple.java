import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map sortMap = null;
    for (Object columnIdentifier : sortMap.key<caret>Set()) {
      Object o = sortMap.get(columnIdentifier);
    }
  }
}
