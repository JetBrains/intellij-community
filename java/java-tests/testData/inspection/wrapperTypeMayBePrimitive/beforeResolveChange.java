// "Convert wrapper type to primitive" "false"

class WrapperType {
  public void objectTest() {
    Integer<caret> first = 2;
    Integer second = 2;
    primitiveUse(first);
    assertEquals(first, second);
  }

  native void assertEquals(int i1, int i2);

  native void assertEquals(Object o1, Object o2);

  native void primitiveUse(int i);
}