class CompoundAssignment {
  public static void main(String[] args) {
    int x = 1;
    x += 2;
    if (<warning descr="Condition 'x == 3' is always 'true'">x == 3</warning>) {}
    long y = 10;
    y /= 5;
    if (<warning descr="Condition 'y == 2' is always 'true'">y == 2</warning>) {}
    byte z = 10;
    z += 300;
    if (<warning descr="Condition 'z > 127' is always 'false'">z > 127</warning>) {}
    Integer boxed = 4;
    boxed += 3;
    if (<warning descr="Condition 'boxed > 6' is always 'true'">boxed > 6</warning>) {}
  }
}