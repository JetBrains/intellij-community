// "Move condition to loop" "false"
class Main {
  public static void main(String[] args) {
    int i = 1;
    do<caret> {
      if (i < 100) {
        break;
      } else {
        i = i * 3;
      }
    } while(true);
  }
}