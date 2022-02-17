// "Qualify the call with 'B'" "true"
class A {
  class B {
    class C {
      static String name() {
        return "";
      }

      class D {
        static String name(String key) {
          return B.name();
        }
      }
    }

    static String name() {
      return "";
    }
  }
}
