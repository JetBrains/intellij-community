enum TestEnum {
  ONE("te<caret>stString");

  TestEnum(String str) {
  }
  
  private class Constants {
    void foo(){}
  }
}