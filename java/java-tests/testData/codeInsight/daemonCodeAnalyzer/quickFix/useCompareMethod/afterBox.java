// "Fix all ''compare()' method can be used to compare primitives' problems in file" "true"
public class Test {
  public void test(int a, int b) {
    if(Integer.compare(a, b) > 0) {
      System.out.println(1);
    }
    if(Long.compare(a, b) > 0) {
      System.out.println(1);
    }
    if(Double.compare(a, b) > 0) {
      System.out.println(1);
    }
  }
}