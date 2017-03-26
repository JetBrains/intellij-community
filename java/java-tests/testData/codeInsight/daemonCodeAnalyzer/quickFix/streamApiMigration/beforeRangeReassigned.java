// "Replace with collect" "false"

public class Test {
  public void test(int n) {
    for(int <caret>i=0; i<n; i++) {
      i = i * 2;
      System.out.println(i);
    }
  }

  public static void main(String[] args) {
    new Test().test(20);
  }
}