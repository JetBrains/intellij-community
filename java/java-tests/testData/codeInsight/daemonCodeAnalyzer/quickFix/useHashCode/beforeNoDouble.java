// "Replace with 'Long.hashCode()'" "true-preview"
public class Test {
  void test(double d) {
    long l = Double.doubleToLongBits(d);
    System.out.println((int) (l ^ (l<caret> >>> 32)));
    System.out.println(l);
  }
}