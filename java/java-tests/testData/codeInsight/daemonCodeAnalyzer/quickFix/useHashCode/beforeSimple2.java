// "Replace with 'Long.hashCode()'" "true-preview"
public class Test {
  long var = 1234567890123456789L;

  public void testMethod() {
    int result = (int<caret>)((var >>> (16+16)) ^ var);
  }
}