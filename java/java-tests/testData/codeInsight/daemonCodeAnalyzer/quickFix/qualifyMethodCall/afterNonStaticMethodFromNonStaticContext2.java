// "Qualify the call with 'A.B.this'" "true-preview"
class A {
  static class B {
    class C {
      String name(String key) {
        return B.this.name();
      }
    }

    String name() {
      return "";
    }
  }
}
