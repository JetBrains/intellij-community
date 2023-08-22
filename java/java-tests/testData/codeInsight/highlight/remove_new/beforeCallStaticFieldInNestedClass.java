// "Remove 'new'" "true"

class A {
  static class Nested {
    static int in = 0;
  }
}

class B {
  {
    int i = /*
     hello,
     world
    */
     new A. // nested
       Nested. /*
       field
       below
       */
       <caret>in;
  }
}