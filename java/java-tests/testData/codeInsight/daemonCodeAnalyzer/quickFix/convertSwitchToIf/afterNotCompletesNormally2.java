// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test4() throws IOException {
    String variable = "abc";
      if (variable.equals("abc")) {
          String s1 = "abcd";
      } else if (variable.equals("def")) {
          String s1 = "abcd";
          myFunction(s1);
      } else {
          throw new IllegalArgumentException();
      }
  }

  public void myFunction(Object o1) throws IOException {
  }
}