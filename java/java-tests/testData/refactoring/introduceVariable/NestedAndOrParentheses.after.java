class X{

  boolean test(String s1, String s2) {
      if (s1 == null) return true;
      if (!s2.equals(s1.trim())) return false;
      boolean foo = s1.isEmpty();
      return foo;
  }

}
