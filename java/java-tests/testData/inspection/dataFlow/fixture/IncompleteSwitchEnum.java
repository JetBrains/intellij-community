public enum Test {
  VALUE;

  void test() {
    Integer code = getCode();
    switch (code) {
      case VALUE.value()<EOLError descr="':' expected"></EOLError>
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