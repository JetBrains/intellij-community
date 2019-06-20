// "Put parameters on separate lines" "false"
// break before rparen

class A {
  void foo(A a1,
           A a2<caret>,
           A a3
  ) {

  }
}