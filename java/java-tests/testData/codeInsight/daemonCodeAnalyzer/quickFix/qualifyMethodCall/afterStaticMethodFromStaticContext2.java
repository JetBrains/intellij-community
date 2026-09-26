// "Qualify the call with 'A'" "true-preview"
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
