// "Move condition to loop" "true"
class Main {
  public static void main(String[] args) {
    int i = 0;
    while<caret> (true) {
      if (i < 13) {
        i++;
      } else {
        break;
      }
    }
  }
}