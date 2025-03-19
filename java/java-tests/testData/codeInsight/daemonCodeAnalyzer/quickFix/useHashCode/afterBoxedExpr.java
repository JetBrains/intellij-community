// "Replace with 'Long.hashCode()'" "true-preview"
public class Test {
  Long var = 1234567890123456789L;
  Long var1 = 1234567890123456784L;

  public void testMethod(boolean f) {
      /*shift amount*/
      int result = (f ? var : var1).hashCode();
  }
}