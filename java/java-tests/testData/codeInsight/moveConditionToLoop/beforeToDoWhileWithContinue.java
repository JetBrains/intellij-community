// "Move condition to loop" "false"
class Main {
  public static void main(String[] args) {
    int i = 0;
    while(true) {
      i++;
      if<caret>(i == 4) continue;
      if(i >= 12) break;
    }
  }
}