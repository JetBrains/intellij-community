// "Move condition to loop" "false"
class Main {
  public static void main(String[] args) {
    int i = 0;
    while(true) {
      i++;
      int j = i;
      if<caret>(j >= 12) break;
    }
  }
}