import java.util.Objects;

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