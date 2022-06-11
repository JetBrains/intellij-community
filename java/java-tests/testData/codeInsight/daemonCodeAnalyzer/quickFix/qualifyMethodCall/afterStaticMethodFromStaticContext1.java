// "Qualify the call with 'A'" "true"
class A {
  static class B {
    String name(String key) {
      return A.name();
    }
  }

  static String name(){
    return "";
  }
}
