// "Qualify the call with 'A.this'" "true-preview"
class A {
  class B {
    static String name(String key) {
      return "";
    }

    {
      String s = A.this.name();
    }
  }

  String name(){
    return "";
  }
}
