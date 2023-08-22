// "Qualify the call with 'A'" "true-preview"
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
