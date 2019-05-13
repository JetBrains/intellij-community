// "Convert to local" "false"
class TestFieldConversion
{
  private static int som<caret>eInt = 0;

  public TestFieldConversion()
  {
    int usingThatInt = someInt;
  }

  /**
   * Referencing that value here {@value #someInt}
   */
  public void someMethod() {

  }
}
