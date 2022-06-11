// "Qualify the call with 'C'" "true"
class A {
  class B {
    class C {
      static String name() {
        return "";
      }

      class D {
        static String name(String key) {
          return C.name();
        }
      }
    }

    static String name() {
      return "";
    }
  }
}
