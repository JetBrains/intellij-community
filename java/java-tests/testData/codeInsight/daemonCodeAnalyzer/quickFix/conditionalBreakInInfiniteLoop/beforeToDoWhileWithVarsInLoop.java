// "Move condition to loop" "false"
class Main {
  public static void main(String[] args) {
    int i = 0;
    while<caret>(true) {
      i++;
      int j = i;
      if(j >= 12) break;
    }
  }
}