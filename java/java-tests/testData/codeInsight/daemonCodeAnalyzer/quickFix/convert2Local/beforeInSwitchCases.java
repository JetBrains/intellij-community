// "Convert to local" "true"
class TestFieldConversion
{
  private int som<caret>eInt = 0;

  public void someMethod(int s) {
    switch (s) {
      case 1:
        System.out.println(someInt);
        break;
      case 3:
        System.out.println(someInt);
        break;
      default:
        break;
    }
  }
}
