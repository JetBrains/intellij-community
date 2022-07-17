// "Qualify the call with 'A'" "true"
class A {
  static class B {
    String name(String key) {
      return name(<caret>);
    }
  }

  static String name(){
    return "";
  }
}
