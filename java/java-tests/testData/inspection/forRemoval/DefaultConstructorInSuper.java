class C {
  @Deprecated(forRemoval=true) C() { }
}

class <error descr="'C()' is deprecated and marked for removal">D</error> extends C {
}

class P {
  void normal() {
    new <error descr="'C()' is deprecated and marked for removal">C</error>(){};
  }

  @SuppressWarnings("deprecation")
  void suppressDeprecation() {
    new <error descr="'C()' is deprecated and marked for removal">C</error>(){};
  }

  @SuppressWarnings("removal")
  void suppressRemoval() {
    new C(){};
  }
}