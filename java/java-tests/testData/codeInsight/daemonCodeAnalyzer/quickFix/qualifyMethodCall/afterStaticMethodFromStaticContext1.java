// "Qualify the call with 'A'" "true-preview"
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
