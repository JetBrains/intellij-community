public enum Test {
  VALUE;

  void test() {
    Integer code = getCode();
    switch (code) {
      case <error descr="Constant expression, pattern or null is required"><warning descr="Switch label 'VALUE.value()' is the only reachable in the whole switch">VALUE.<error descr="Cannot resolve symbol 'value'">value</error>()</warning></error><EOLError descr="':' expected"></EOLError>
    }
    if (code == VALUE.value()) {
      getCode();
    }

  }

  int value() {
    return ordinal();
  }

  public native Integer getCode();
}