class X {
  // IDEA-246519
  // IDEA-246500
  void test() {
    Byte b = 1;
    Short s = 1;
    Integer i = 1;
    if (<warning descr="Condition 'b instanceof Byte' is always 'true'">b instanceof Byte</warning>) {}
    if (<warning descr="Condition 's instanceof Short' is always 'true'">s instanceof Short</warning>) {}
    if (<warning descr="Condition 'b.equals(1)' is always 'false'">b.equals(1)</warning>) {}
    if (<warning descr="Condition 's.equals(1)' is always 'false'">s.equals(1)</warning>) {}
    if (<warning descr="Condition 'b.equals(i)' is always 'false'">b.equals(i)</warning>) {}
    if (<warning descr="Condition 'b.equals((byte)1)' is always 'true'">b.equals((byte)1)</warning>) {}
    if (<warning descr="Condition 's.equals((short)1)' is always 'true'">s.equals((short)1)</warning>) {}
  }
}