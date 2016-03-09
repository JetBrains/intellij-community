class Test {

  {
    pair<error descr="'pair(byte)' in 'Test' cannot be applied to '(int)'">(2)</error>;
  }

  static <T> void pair( byte b) {}
}