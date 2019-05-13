public class A {

  public void testInlineRefactoring() {
    int[] ar<caret>ray = ar();
    array[1] = 22;
  }

  private int[] ar() {
    return new int[0];
  }
}