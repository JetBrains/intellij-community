// "Qualify the call with 'A.this'" "true-preview"
class Base {
  String name() {
    return "";
  }
}

class A extends Base {
  class B {
    String name(String key) {
      return A.this.name();
    }
  }

  public String name() {
    return "";
  }
}
