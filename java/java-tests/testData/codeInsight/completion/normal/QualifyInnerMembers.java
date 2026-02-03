class C1 {
 static enum E {
   FOO, BAAAAAAAAAR;
 }
}

class C2 {
  void f() {
      C1.E e = BAAAA<caret>;
  }
}
