class ImplicitToString {

  void testStringBuilder() {
    StringBuilder sb<caret> = new StringBuilder();
    sb.append("World!");
    System.out.println("Hello " + sb);
  }
}