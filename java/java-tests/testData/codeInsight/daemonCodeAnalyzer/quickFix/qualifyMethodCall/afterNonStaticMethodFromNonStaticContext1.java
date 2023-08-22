// "Qualify the call with 'A.this'" "true-preview"
class A {
  class B {
    String name(String key) {
      return A.this.name();
    }
  }

  String name(){
    return "";
  }
}
