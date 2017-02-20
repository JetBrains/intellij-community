// "Replace with collect" "false"

public class Test {
  public void test(int n) {
    for(int <caret>i=0; i<n; i++) {
      for(int j=0; j<i; j++) {
        j = j * 2;
        System.out.println(j);
      }
    }
  }

  public static void main(String[] args) {
    new Test().test(20);
  }
}