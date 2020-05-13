class Test {
  // len > _
  public int[] lengthBiggerSrc() {
    int[] src = new int[] { 1, 2, 3 };
    int[] dest = new int[] { 4, 5, 6, 7, 8, 9 };
    System.<warning descr="The call to 'arraycopy' always fails as index is out of bounds">arraycopy</warning>(src, 1, dest, 0, 4);
    return dest;
  }

  public int[] lengthBiggerDest() {
    int[] src = new int[] { 1, 2, 3 };
    int[] dest = new int[] { 4 };
    System.<warning descr="The call to 'arraycopy' always fails as index is out of bounds">arraycopy</warning>(src, 1, dest, 0, 2);
    return dest;
  }

  // 0 > _
  public int[] srcPosNegative() {
    int[] src = new int[] { 1, 2, 3 };
    int[] dest = new int[] { 4, 5, 6 };
    System.<warning descr="The call to 'arraycopy' always fails as index is out of bounds">arraycopy</warning>(src, -1, dest, 0, 2);
    return dest;
  }

  public int[] destPosNegative() {
    int[] src = new int[] { 1, 2, 3 };
    int[] dest = new int[] { 4, 5, 6 };
    System.<warning descr="The call to 'arraycopy' always fails as index is out of bounds">arraycopy</warning>(src, 0, dest, -1, 2);
    return dest;
  }

  public int[] lengthNegative() {
    int[] src = new int[] { 1, 2, 3 };
    int[] dest = new int[] { 4, 5, 6 };
    System.<warning descr="The call to 'arraycopy' always fails as index is out of bounds">arraycopy</warning>(src, 0, dest, 0, -1);
    return dest;
  }

  public int[] copyNothing() {
    int[] src = new int[] {  };
    int[] dest = new int[] {  };
    System.arraycopy(src, 0, dest, 0, 0);
    return dest;
  }
}