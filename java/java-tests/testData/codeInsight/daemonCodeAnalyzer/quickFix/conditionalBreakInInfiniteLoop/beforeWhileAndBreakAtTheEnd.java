// "Move condition to loop" "false"
class Main {
  public static void main(String[] args) {
    int i = 1;
    while<caret>(i % 10 != 0) {
      i = i * 2;
      if(i > 100) break;
      }
  }
}