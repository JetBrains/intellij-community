class X{

  boolean test(String s1, String s2) {
    return s1 == null || (s2.equals(s1.trim()) && <selection>s1.isEmpty()</selection>);
  }

}
