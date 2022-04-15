class Foo {
  void foo(){
    public void testDocComment2() throws Exception{
      doTextTest("class Test {\n" +
               "/**\n" +
               "/**\n" +
               "/**\n" +
               "/**\n<caret>" +
               "}");
  }

  }
}