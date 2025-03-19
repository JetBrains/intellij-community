class A {
  private static final short MY_CODE = 1;

  {
    int code;
    code = <caret>MY_CODE; // invoke quick fix here
  }
}