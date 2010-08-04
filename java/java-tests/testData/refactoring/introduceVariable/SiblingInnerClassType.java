class A {
  class B {
  }

  class C {
     void testHere() {
       new <caret>B()
     }
  }
}