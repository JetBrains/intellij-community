public enum Test {
  VALUE;

  void test() {
    Integer code = getCode();
    switch (code) {
      case <error descr="Constant expression, pattern or null is required">VALUE.<error descr="Cannot resolve symbol 'value'">value</error>()</error><EOLError descr="':' or '->' expected"></EOLError>
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