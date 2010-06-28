import java.util.TreeMap;
import java.util.HashMap;
import java.util.Map;

class Test {
  boolean f () {
    return false;
  }

  Boolean g (int i) {
    //This is OK thanks to boxing f()
    return i > 0 ? f () : null;
  }

  {
    Object values = new Object();
    //IDEADEV-1756: this should be OK
    final Map<Object,Object> newValues = true ? new TreeMap<Object,Object>() : new HashMap<Object,Object>();
    newValues.get(values);
  }
}