class Test {
  interface Smth {
    boolean isSelected();
  }

  private static String test(Smth cb1, Smth cb2) {
    int mask = (cb1.isSelected() ? 1 : 0) << 1 | (cb2.isSelected() ? 1 : 0);
    return (mask == 0 ? "A" : (mask == 3 ? "B" : mask == 2 ? "C" : <warning descr="Condition 'mask > 3' is always 'false'">mask > 3</warning> ? "D" : <warning descr="Condition 'mask < 0' is always 'false'">mask < 0</warning> ? "E" : "F"));
  }
}