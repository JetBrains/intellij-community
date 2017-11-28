import foo.*;
class MyTest {
  {
    Assert.assert<caret>True(false);
  }
  
  static void assertTrue() {}
  static void assertTrue(String message, boolean flag) {}
}