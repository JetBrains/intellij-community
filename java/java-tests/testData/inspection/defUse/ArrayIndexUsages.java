class Foo {
  int myOffset;
  void foo() {
    int offset = myOffset + 4;
    byte[] a = new byte[10];
    byte b1 = a[offset++];
    byte b2 = a[<warning descr="The value changed at 'offset++' is never used">offset++</warning>];
    System.out.println(b1);
    System.out.println(b2);
  }
}