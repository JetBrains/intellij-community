// "Replace with 'Long.hashCode()'" "true-preview"
public class Test {
  void test(double d) {
    long l = Double.doubleToLongBits(d);
    d++;
    System.out.println((int) (l<caret> ^ (l >>> 32)));
  }
}