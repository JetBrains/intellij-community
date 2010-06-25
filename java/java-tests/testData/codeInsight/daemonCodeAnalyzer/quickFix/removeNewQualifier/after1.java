// "Remove qualifier" "true"

class i2 {
    class A {
        A(b b) {
          <caret>new b.c();
        }
    }

    static class b {
      static class c {}
    }

}
