// "Replace with forEach" "false"
public class Main {
  public void test(int[] arr) {
    for(int i : arr) {
      if(i > 0) {
        System.out.println(i);
      }
    }
  }
}