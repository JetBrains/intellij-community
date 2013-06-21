public class A {

  public void testInlineRefactoring() {
    int[] array = ar();
    arr<caret>ay[1] = 22;
  }

  private int[] ar() {
    return new int[0];
  }
}