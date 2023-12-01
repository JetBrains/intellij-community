// "Replace with 'Long.hashCode()'" "true-preview"
public class Test {
  Long var = 1234567890123456789L;

  public void testMethod() {
    int result = var.hashCode();
  }
}