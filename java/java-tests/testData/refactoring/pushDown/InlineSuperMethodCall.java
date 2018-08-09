class Test {
 void fo<caret>o(final boolean d) {
   if (d) {
     foo(false);
   }
   System.out.println();

 }
}

class Test2 extends Test {
  @Override
  void foo(final boolean d) {
    super.foo(d);
  }
}
