class C {
  @Deprecated(forRemoval=true) C() { }
}

class D extends C {
}

class P {
  void normal() {
    new C(){};
  }

  @SuppressWarnings("deprecation")
  void suppressDeprecation() {
    new C(){};
  }

  @SuppressWarnings("removal")
  void suppressRemoval() {
    new C(){};
  }
}