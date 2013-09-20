import java.util.*;

class Test {
  {
    Object obj = new Object();
    Set<Class<?>> types = Collections.singleton(obj.getClass());
  }
}
