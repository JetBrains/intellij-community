import java.util.*;
import org.jetbrains.annotations.*;

class ClassMethodsInlining {
  void assignableTest3(Class<?> c, List<?> list) {
    if(c.isAssignableFrom(list.get(0).getClass())) {
      System.out.println("possible");
    }
  }

  void instanceTest(Class<?> c, Object o) {
    if(c.isInstance(o)) {
      System.out.println(o.hashCode());
    }
  }

  void instanceNotNullTest(Class<?> c, Object o) {
    if(o != null && c.isInstance(o)) {
      System.out.println(o.hashCode());
    }
    if(c.isInstance(new Object())) {
      System.out.println("possible");
    }
  }

  void assignableTest(Class<?> c1, Class<?> c2) {
    if(c1.isAssignableFrom(c2)) {
      System.out.println("possible");
    }
  }

  void assignableTest2(Class<?> c) {
    if(c.isAssignableFrom(String.class)) {
      System.out.println("possible");
    }
    if(String.class.isAssignableFrom(c)) {
      System.out.println("possible");
    }
  }

  void testNullFalse(@Nullable Object s, Class<?> c) {
    if(c.isInstance(s)) {
      System.out.println(s.hashCode());
    }
  }

  void limitedClass(List<String> list, Class<?> c) {
    assert c == Integer.class || c == String.class || c == Boolean.class;

    if(<warning descr="Condition 'c.isInstance(list)' is always 'false'">c.isInstance(list)</warning>) {
      System.out.println("oops");
    }
  }

  void primitiveClass(Object obj) {
    // Not supported yet
    if(int.class.isInstance(obj)) {
      System.out.println("impossible");
    }
  }

  void objectClass(Object obj, Object obj2) {
    Class<?> cls = Object.class;
    if(<warning descr="Condition 'cls.isInstance(obj)' is redundant and can be replaced with a null check">cls.isInstance(obj)</warning>) {
      System.out.println("for every non-null");
    }
    if(<warning descr="Condition 'cls.isAssignableFrom(obj2.getClass())' is always 'true'">cls.isAssignableFrom(obj2.getClass())</warning>) {
      System.out.println("redundant");
    }
  }

  void incompatibleClasses() {
    Class<?> c1 = Integer.class;
    Class<?> c2 = Double.class;
    if(<warning descr="Condition 'c1.isAssignableFrom(c2)' is always 'false'">c1.isAssignableFrom(c2)</warning>) {
      System.out.println("impossible");
    }
  }

  void testEphemeral(Object obj, Class<?> c) {
    if(c.isInstance(obj)) {
      System.out.println("yeah!");
    }
    System.out.println(obj.hashCode());
  }

  native Object next(Object prev);

  void testLoop(Object o) {
    while(o != null && !(o instanceof String)) {
      o = next(o);
    }
    if(<warning descr="Condition 'o instanceof String' is redundant and can be replaced with a null check">o instanceof String</warning>) {
      System.out.println("String");
    }
  }
}
