class ImplicitToString3 {

  void testStringBuilder() {
    StringBuilder sb<caret> = new StringBuilder();
    System.out.println("Hello " + sb);
  }
}