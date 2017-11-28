// "Replace 'switch' with 'if'" "true"
class X {
  void test4() throws IOException {
    String variable = "abc";
      if ("abc".equals(variable)) {
          String s1 = "abcd";
      } else if ("def".equals(variable)) {
          String s1 = "abcd";
          myFunction(s1);
      } else {
          throw new IllegalArgumentException();
      }
  }

  public void myFunction(Object o1) throws IOException {
  }
}