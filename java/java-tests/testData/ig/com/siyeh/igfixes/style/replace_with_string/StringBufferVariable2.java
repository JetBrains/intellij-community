class StringBufferVariable2 {
  void foo() {
    StringBuffer <caret>sb = new StringBuffer();
    System.out.println(sb.toString());
  }
}