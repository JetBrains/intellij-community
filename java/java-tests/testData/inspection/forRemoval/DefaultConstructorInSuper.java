class C {
  @Deprecated(forRemoval=true) C() { }
}

class <error descr="Default constructor in 'C' is deprecated and marked for removal">D</error> extends C {
}

class P {
  void normal() {
    new <error descr="Default constructor in 'C' is deprecated and marked for removal">C</error>(){};
  }

  @SuppressWarnings("deprecation")
  void suppressDeprecation() {
    new <error descr="Default constructor in 'C' is deprecated and marked for removal">C</error>(){};
  }

  @SuppressWarnings("removal")
  void suppressRemoval() {
    new C(){};
  }
}