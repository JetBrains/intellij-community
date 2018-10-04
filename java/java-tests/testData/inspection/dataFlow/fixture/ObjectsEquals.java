import java.util.Objects;

class NotComplex {
  Object o1, o2, o3, o4, o5, o6, o7, o8, o9, o10;

  boolean equal(NotComplex other) {
    return Objects.equals(o1, other.o1) &&
           Objects.equals(o2, other.o2) &&
           Objects.equals(o3, other.o3) &&
           Objects.equals(o4, other.o4) &&
           Objects.equals(o5, other.o5) &&
           Objects.equals(o6, other.o6) &&
           Objects.equals(o7, other.o7) &&
           Objects.equals(o8, other.o8) &&
           Objects.equals(o9, other.o9) &&
           Objects.equals(o10, other.o10);
  }
}

class Main {
  void testObjectsNullity(Object val1, Object val2) {
    if(Objects.equals(val1, val2)) {
      if(val1 == null && <warning descr="Condition 'val2 == null' is always 'true' when reached">val2 == null</warning>) {}
      if(val2 != null && <warning descr="Condition 'val1 != null' is always 'true' when reached">val1 != null</warning>) {}
    }
    if(val2 == null && !Objects.equals(val1, val2)) {
      if(<warning descr="Condition 'val1 == null' is always 'false'">val1 == null</warning>) {}
    }
  }

  void testEquality(Object val1, Object val2) {
    if(<warning descr="Condition '!Objects.equals(val1, val2) && val1 == val2' is always 'false'">!Objects.equals(val1, val2) && <warning descr="Condition 'val1 == val2' is always 'false' when reached">val1 == val2</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testStrings() {
    String s = "foobar";
    String s1 = s.substring(3);
    if(<warning descr="Condition 'Objects.equals(s1, \"bar\")' is always 'true'">Objects.equals(s1, "bar")</warning>) {
      System.out.println("always");
    }
  }
}