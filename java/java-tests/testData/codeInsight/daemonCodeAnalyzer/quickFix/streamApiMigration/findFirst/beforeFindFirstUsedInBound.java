// "Replace with findFirst()" "false"

public class Main {
  public boolean check(int value) {
    return value == 3;
  }

  public void find() {
    int end = 10;
    for(<caret>int i=0; i<end; i++) {
      if(check(i)) {
        end = i;
        break;
      }
    }
    System.out.println(end);
  }
}