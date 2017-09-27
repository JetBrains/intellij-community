// "Replace with forEach" "false"

public class Main {

  public long test() {
    int j = 0;
    for <caret>(int i = 0; j < 10; j = j + i) {
      if(i < 3) {
        System.out.println(i);
      }
    }
  }
}