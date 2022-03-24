// "Qualify the call with 'A.this'" "true"
class Base {
  String name() {
    return "";
  }
}

class A extends Base {
  class B {
    String name(String key) {
      return name(<caret>);
    }
  }
}
