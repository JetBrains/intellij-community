// "Qualify the call with 'C'" "true-preview"
class A {
  class B {
    class C {
      static String name() {
        return "";
      }

      class D {
        static String name(String key) {
          return name(<caret>);
        }
      }
    }

    static String name() {
      return "";
    }
  }
}
