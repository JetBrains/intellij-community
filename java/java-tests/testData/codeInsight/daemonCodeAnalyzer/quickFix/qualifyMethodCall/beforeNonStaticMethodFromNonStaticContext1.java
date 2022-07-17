// "Qualify the call with 'A.this'" "true"
class A {
  class B {
    String name(String key) {
      return name(<caret>);
    }
  }

  String name(){
    return "";
  }
}
