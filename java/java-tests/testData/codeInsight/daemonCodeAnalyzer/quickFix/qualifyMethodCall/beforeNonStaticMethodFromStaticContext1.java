// "Qualify the call with 'A.this'" "false"
class A {
  static class B {
    String name(String key) {
      return name(<caret>);
    }
  }

  String name(){
    return "";
  }
}
