
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

class Main {
  <T extends Runnable> List<T> foo(T t) {
    t.run();
    return Collections.emptyList();
  }

  <T extends Serializable> void m(Object[] obj) {
    List<?> r1 = foo(null);
    List<? extends Serializable> r2 =   foo(null);
    List<? super String> r3 = foo<error descr="'foo(? super java.lang.String & java.lang.Runnable)' in 'Main' cannot be applied to '()'">( )</error>;
    List<? extends Runnable> r4 =  foo(null);
  }
}

class Main2 {
  <T extends Serializable> List<T> foo(Class<T> c, String b, Object ... a) {
    return Collections.emptyList();
  }
  <T extends Serializable> List<T> foo(String b, Object ... a) {
    return Collections.emptyList();
  }

  <T extends Serializable> void m(Object[] obj) {
    List r1 = foo("", obj);
    List<T> r2 = foo("", obj);
    List<?> r3 = foo("", obj);
    <error descr="Incompatible types. Found: 'java.util.List<java.io.Serializable>', required: 'java.util.List<java.lang.Object>'">List<Object> r4 = foo("", obj);</error>
  }
}