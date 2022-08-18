package test;

public class TestClass {

  public void test() {

      compareStrings(Global<caret>Settings.getInstance().IGNORE_FILES_LIST, a); 
  }
}