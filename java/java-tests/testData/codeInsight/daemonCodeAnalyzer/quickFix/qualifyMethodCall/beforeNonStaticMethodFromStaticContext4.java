// "Qualify the call with 'A.this'" "false"
class A {
  class B {
    static String name(String key) {
      return "";
    }

    static {
      String s = name(<caret>);
    }
  }

  String name(){
    return "";
  }
}
