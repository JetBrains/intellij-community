// "Replace with 'Long.hashCode()'" "true-preview"
public class Test {
  long var = 1234567890123456789L;

  public void testMethod() {
      /*shift amount*/
      int result = Long.hashCode(var);
  }
}