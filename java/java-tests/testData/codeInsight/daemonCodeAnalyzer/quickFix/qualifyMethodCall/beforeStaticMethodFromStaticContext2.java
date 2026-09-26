// "Qualify the call with 'A'" "true-preview"
class A {
  class B {
    static String name(String key) {
      return name(<caret>);
    }
  }

  static String name(){
    return "";
  }
}
