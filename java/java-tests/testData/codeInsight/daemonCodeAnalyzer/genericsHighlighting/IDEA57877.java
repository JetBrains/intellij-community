import java.util.*;

class Test {
  {
    Object obj = new Object();
    <error descr="Incompatible types. Found: 'java.util.Set<java.lang.Class<capture<? extends java.lang.Object>>>', required: 'java.util.Set<java.lang.Class<?>>'">Set<Class<?>> types = Collections.singleton(obj.getClass());</error>
  }
}
