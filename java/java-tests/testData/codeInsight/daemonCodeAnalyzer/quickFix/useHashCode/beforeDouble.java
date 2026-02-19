// "Replace with 'Double.hashCode()'" "true-preview"
public class Test {
  void test(double d) {
    long l = Double.doubleToLongBits(d);
    System.out.println((int) (l<caret> ^ (l >>> 32)));
  }
}