public class C <T> {
  <T> T foo () {return null;}

  class Sub {
    T foo () {
      return C.this.<ref>foo();
    }
  }
}