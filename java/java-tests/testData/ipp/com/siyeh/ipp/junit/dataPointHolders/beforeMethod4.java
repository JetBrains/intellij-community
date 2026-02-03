// "Replace by @DataPoint field" "false"
class Foo {

  //wrong @DataPoint method declaration (returnType == void)
  @org.junit.experimental.theories.DataPoint
  public static void ba<caret>r() {
  }

}
