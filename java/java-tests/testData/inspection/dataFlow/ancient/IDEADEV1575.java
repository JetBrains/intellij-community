class TestGenericsInstanceof {
    public void foo(Object o) {
      boolean test = true;
      <warning descr="Condition 'test' at the left side of assignment expression is always 'true'. Can be simplified">test</warning> &= o.hashCode() > 3;
    }
}
