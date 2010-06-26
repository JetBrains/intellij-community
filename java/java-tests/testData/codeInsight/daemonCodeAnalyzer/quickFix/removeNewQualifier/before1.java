// "Remove qualifier" "true"

class i2 {
    class A {
        A(b b) {
          <caret>b.new c();
        }
    }

    static class b {
      static class c {}
    }

}
