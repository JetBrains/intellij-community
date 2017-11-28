// "Fix all ''compare()' method can be used to compare primitives' problems in file" "true"
public class Test {
  public void test(int a, int b) {
    if(((Integer)a).compa<caret>reTo(b) > 0) {
      System.out.println(1);
    }
    if(Long.valueOf(a).compareTo(Long.valueOf(b)) > 0) {
      System.out.println(1);
    }
    if(new Double(a).compareTo(new Double(b)) > 0) {
      System.out.println(1);
    }
  }
}