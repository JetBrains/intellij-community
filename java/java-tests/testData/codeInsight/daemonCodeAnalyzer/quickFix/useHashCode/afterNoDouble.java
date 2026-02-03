// "Replace with 'Long.hashCode()'" "true-preview"
public class Test {
  void test(double d) {
    long l = Double.doubleToLongBits(d);
    System.out.println(Long.hashCode(l));
    System.out.println(l);
  }
}