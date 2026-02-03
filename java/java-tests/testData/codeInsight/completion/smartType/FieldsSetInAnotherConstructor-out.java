class A {
  int doo;
  int aaa;
  A(int b) {
    doo = b;
  }

  A(int aac, int aad) {
    this(aac);
    aaa = doo;<caret>
  }
}

