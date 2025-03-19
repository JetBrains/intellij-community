// "Replace with 'Long.hashCode()'" "false"
public class Test {
  long var = 1234567890123456789L;

  public void testMethod() {
    int result = (int<caret>)((var++) ^ ((var++) >>> /*shift amount*/ 32));
  }
}