import java.util.*;

class GetClass {
  native void unknown();

  public static void testIntClass() {
    Class<?> cls = someIntClass();
    System.out.println(Object.class.isAssignableFrom(cls));
  }

  public static Class<?> someIntClass() {
    return int.class;
  }

  void testStability(Object obj, Class<?> c) {
    if (obj.getClass().equals(c)) {
      unknown();
      if (<warning descr="Condition 'obj.getClass().equals(c)' is always 'true'">obj.getClass().equals(c)</warning>) { }
    }
    if (obj.getClass().equals(ArrayList.class)) {
      unknown();
      if (<warning descr="Condition 'obj.getClass().equals(ArrayList.class)' is always 'true'">obj.getClass().equals(ArrayList.class)</warning>) { }
    }
  }

  void testFinalClass(String s) {
    if (<warning descr="Condition 's.getClass() == String.class' is always 'true'">s.getClass() == String.class</warning>) { }
    if (<warning descr="Condition 'String.class.equals(s.getClass())' is always 'true'">String.class.equals(s.getClass())</warning>) {}

  }

  void testNew() {
    Object x = new HashSet();
    if (<warning descr="Condition 'x.getClass() == HashSet.class' is always 'true'">x.getClass() == HashSet.class</warning>) {}
  }

  void testInstanceOfInterop(Object obj) {
    if (obj instanceof CharSequence) {
      if (<warning descr="Condition 'obj.getClass() == Integer.class' is always 'false'">obj.getClass() == Integer.class</warning>) {}
    }
    if (obj.getClass() == HashSet.class) {
      if (<warning descr="Condition 'obj instanceof Set' is always 'true'">obj instanceof Set</warning>) {}
      if (<warning descr="Condition 'obj instanceof LinkedHashSet' is always 'false'">obj instanceof LinkedHashSet</warning>) {}
    }
    if (obj instanceof HashSet) {
      if (obj.getClass() == HashSet.class) {} // possible but not always
      if (obj.getClass() == LinkedHashSet.class) {} // also possible
    }
  }

  void testInterfaceAbstract(Object obj, Class<?> c) {
    if (<warning descr="Condition 'obj.getClass() == CharSequence.class' is always 'false'">obj.getClass() == CharSequence.class</warning>) {}
    if (<warning descr="Condition 'obj.getClass().equals(Number.class)' is always 'false'">obj.getClass().equals(Number.class)</warning>) {}
    if (c == Number.class || c == CharSequence.class) {
      if (<warning descr="Condition 'obj.getClass() == c' is always 'false'">obj.getClass() == c</warning>) {}
    }
  }
  
  void testPrimitive(Object obj) {
    Class<?> c = int.class;
    if (<warning descr="Condition 'obj.getClass() == c' is always 'false'">obj.getClass() == c</warning>) {}
    if (<warning descr="Condition 'obj.getClass() == void.class' is always 'false'">obj.getClass() == void.class</warning>) {}
    if (obj.getClass() == int[].class) {}
  }
  
  void testVoidType(Object obj) {
    if (<warning descr="Condition 'obj.getClass() == Void.class' is always 'false'">obj.getClass() == Void.class</warning>) {}
  }

  void testIntermediateVar(Object obj) {
    Class<?> c = obj.getClass();
    if (<warning descr="Condition 'c == Number.class' is always 'false'">c == Number.class</warning>) {}
    if (c == HashSet.class) {
      if (<warning descr="Condition 'obj instanceof CharSequence' is always 'false'">obj instanceof CharSequence</warning>) {}
    }
    if (obj instanceof CharSequence) {
      if (<warning descr="Condition 'c == HashSet.class' is always 'false'">c == HashSet.class</warning>) {}
    }
  }

  void testTwoObjects(Object o1, Object o2) {
    if (o1 instanceof CharSequence && o2 instanceof Integer) {
      if (<warning descr="Condition 'o1.getClass() == o2.getClass()' is always 'false'">o1.getClass() == o2.getClass()</warning>) {}
    }
    if (o1.getClass() == o2.getClass()) {
      if (o1 instanceof String) {
        if(<warning descr="Condition 'o2.getClass() == Integer.class' is always 'false'">o2.getClass() == Integer.class</warning>) {}
      }
      if (o1 instanceof CharSequence) {
        if (<warning descr="Condition 'o2 instanceof Integer' is always 'false'">o2 instanceof Integer</warning>) {}
      }
    }
  }
  
  void testGetSimpleName(Object obj) {
    if (obj.getClass() == Integer.class || obj.getClass() == Long.class) {
      Class<?> cls = obj.getClass();
      if (<warning descr="Condition 'cls.getSimpleName().startsWith(\"X\")' is always 'false'">cls.getSimpleName().startsWith("X")</warning>) {}
      if (cls.getSimpleName().startsWith("I")) {
        System.out.println((<warning descr="Casting 'obj' to 'Long' will produce 'ClassCastException' for any non-null value">Long</warning>)obj);
      }
    }
  }
}