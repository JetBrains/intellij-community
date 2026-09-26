class StringBufferVariable {
  void foo() {
    StringBuffer <caret>sb = new StringBuffer("asdf").append("asdf");
    System.out.println(sb.toString());
  }
}