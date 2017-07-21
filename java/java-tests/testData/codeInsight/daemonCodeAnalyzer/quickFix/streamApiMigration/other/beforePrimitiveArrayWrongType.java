// "Replace with forEach" "false"
public class Main {
  public void test(int[] arr) {
    for(float i : a<caret>rr) {
      if(i > 0) {
        System.out.println(i);
      }
    }
  }
}