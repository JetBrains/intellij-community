// "Move condition to loop" "false"
class Main {
  public static void main(String[] args) {
    int i = 0;
    int j = 0;
    annoyingLabel: while(j++ < 100) {
      while<caret>(true) {
        i++;
        if(i < 0) {
          break annoyingLabel;
        }
      }
    }
  }
}