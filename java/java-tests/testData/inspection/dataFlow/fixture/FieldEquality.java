class Point {
  int x, y;

  void check(Point other) {
    if(<warning descr="Condition 'x != other.x && this == other' is always 'false'">x != other.x && <warning descr="Condition 'this == other' is always 'false' when reached">this == other</warning></warning>) {
      System.out.println("Impossible");
    }
  }

}
