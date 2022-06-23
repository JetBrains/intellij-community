public enum Test {
  VALUE;

  void test() {
    Integer code = getCode();
    switch (code) {
      case <warning descr="Switch label 'VALUE.value()' is the only reachable in the whole switch">VALUE.<error descr="Cannot resolve symbol 'value'">value</error>()</warning><EOLError descr="':' expected"></EOLError>
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