class TestGenericsInstanceof {
    public void foo(Object o) {
      boolean test = true;
      test &= o.hashCode() > 3;
    }
}
