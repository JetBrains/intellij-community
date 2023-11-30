class A {
  private static final short MY_CODE = 1;

  {
    int code;
    code = (int) MY_CODE; // invoke quick fix here
  }
}