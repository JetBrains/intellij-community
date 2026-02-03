import java.util.*;

class Test {
  {
    Object obj = new Object();
    Set<Class<?>> types = Collections.<error descr="Incompatible types. Found: 'java.util.Set<java.lang.Class<capture<? extends java.lang.Object>>>', required: 'java.util.Set<java.lang.Class<?>>'">singleton</error>(obj.getClass());
  }
}
