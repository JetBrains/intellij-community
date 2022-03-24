// "Qualify the call with 'A.this'" "false"
class A {
  class B {
    static String name(String key) {
      return name(<caret>);
    }
  }

  String name(){
    return "";
  }
}
