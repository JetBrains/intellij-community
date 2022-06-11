// "Qualify the call with 'A'" "true"
class A {
  class B {
    static String name(String key) {
      return A.name();
    }
  }

  static String name(){
    return "";
  }
}
