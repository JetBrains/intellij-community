// "Replace with forEach" "true"
public class Main {
  public void test(int[] arr) {
    for(int i : a<caret>rr) {
      if(i > 0) {
        System.out.println(i);
      }
    }
  }
}