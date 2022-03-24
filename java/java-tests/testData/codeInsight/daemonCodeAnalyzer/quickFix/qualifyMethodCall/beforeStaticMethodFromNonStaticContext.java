// "Qualify the call with 'A'" "true"
class A {
  class B {
    String name(String key) {
      return name(<caret>);
    }
  }

  static String name(){
    return "";
  }
}
