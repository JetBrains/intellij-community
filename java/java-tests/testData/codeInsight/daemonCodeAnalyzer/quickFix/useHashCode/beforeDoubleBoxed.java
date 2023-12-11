// "Replace with 'Double.hashCode()'" "true-preview"
public class Test {
  void test(Double d) {
    long l = Double.doubleToLongBits(/*comment*/d);
    System.out.println((int) (l ^ <caret>(l >>> 32)));
  }
}