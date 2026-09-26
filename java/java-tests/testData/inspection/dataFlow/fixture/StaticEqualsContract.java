import java.util.*;

class A {
  void testEqualsString(String s, String s2) {
    if (s.equals(s2)) {
      if (<warning descr="Condition 'Objects.equals(s, s2)' is always 'true'">Objects.equals(s, s2)</warning>) {}
    } else {
      if (<warning descr="Condition 'Objects.equals(s, s2)' is always 'false'">Objects.equals(s, s2)</warning>) {}
    }
    if (Objects.equals(s, s2)) {
      if (<warning descr="Condition 's.equals(s2)' is always 'true'">s.equals(s2)</warning>) {}
      if (<warning descr="Condition 's.length() == s2.length()' is always 'true'">s.length() == s2.length()</warning>) {}
    } else {
      if (<warning descr="Condition 's.equals(s2)' is always 'false'">s.equals(s2)</warning>) {}
      if (s.length() == s2.length()) {}
    }
  }

  void testEqualsCls(Class<?> s, Class<?> s2) {
    if (s.equals(s2)) {
      if (<warning descr="Condition 'Objects.equals(s, s2)' is always 'true'">Objects.equals(s, s2)</warning>) {}
    } else {
      if (<warning descr="Condition 'Objects.equals(s, s2)' is always 'false'">Objects.equals(s, s2)</warning>) {}
    }
    if (Objects.equals(s, s2)) {
      if (<warning descr="Condition 's.equals(s2)' is always 'true'">s.equals(s2)</warning>) {}
    } else {
      if (<warning descr="Condition 's.equals(s2)' is always 'false'">s.equals(s2)</warning>) {}
    }
  }

  void testEqualsList(List<?> s, List<?> s2) {
    if (s.equals(s2)) {
      if (Objects.equals(s, s2)) {}
    } else {
      if (Objects.equals(s, s2)) {}
    }
    if (Objects.equals(s, s2)) {
      if (s.equals(s2)) {}
    } else {
      if (s.equals(s2)) {}
    }
  }

  void testEqualsArrays(String[] s, String[] s2) {
    if (s.equals(s2)) {
      if (<warning descr="Condition 'Objects.equals(s, s2)' is always 'true'">Objects.equals(s, s2)</warning>) {}
    } else {
      if (<warning descr="Condition 'Objects.equals(s, s2)' is always 'false'">Objects.equals(s, s2)</warning>) {}
    }
    if (Objects.equals(s, s2)) {
      if (<warning descr="Condition 's.equals(s2)' is always 'true'">s.equals(s2)</warning>) {}
      if (<warning descr="Condition 's.length == s2.length' is always 'true'">s.length == s2.length</warning>) {}
    } else {
      if (<warning descr="Condition 's.equals(s2)' is always 'false'">s.equals(s2)</warning>) {}
      if (s.length == s2.length) {}
    }
  }

  void testEqualsArrays2(String[] s, String[] s2) {
    if (s.equals(s2)) {
      if (<warning descr="Condition 'Arrays.equals(s, s2)' is always 'true'">Arrays.equals(s, s2)</warning>) {}
    } else {
      if (Arrays.equals(s, s2)) {}
    }
    if (Arrays.equals(s, s2)) {
      if (s.equals(s2)) {}
      if (<warning descr="Condition 's.length == s2.length' is always 'true'">s.length == s2.length</warning>) {}
    } else {
      if (<warning descr="Condition 's.equals(s2)' is always 'false'">s.equals(s2)</warning>) {}
      if (s.length == s2.length) {}
    }
  }

  void testEqualsArrays3(String[] s, String[] s2) {
    if (s.equals(s2)) {
      if (<warning descr="Condition 'Arrays.deepEquals(s, s2)' is always 'true'">Arrays.deepEquals(s, s2)</warning>) {}
    } else {
      if (Arrays.deepEquals(s, s2)) {}
    }
    if (Arrays.deepEquals(s, s2)) {
      if (s.equals(s2)) {}
      if (<warning descr="Condition 's.length == s2.length' is always 'true'">s.length == s2.length</warning>) {}
    } else {
      if (<warning descr="Condition 's.equals(s2)' is always 'false'">s.equals(s2)</warning>) {}
      if (s.length == s2.length) {}
    }
  }

}
