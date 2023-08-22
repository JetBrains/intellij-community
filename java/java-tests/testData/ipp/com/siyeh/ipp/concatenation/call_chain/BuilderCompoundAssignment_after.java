class Test {
  {
    String s = "";
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("foo");
      stringBuilder.append("bar");
      s += stringBuilder.toString();
  }
}