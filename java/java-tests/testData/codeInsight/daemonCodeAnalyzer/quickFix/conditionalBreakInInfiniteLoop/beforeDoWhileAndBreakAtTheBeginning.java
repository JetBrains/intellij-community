// "Move condition to loop" "false"
class Main {
  public static void main(String[] args) {
    int i = 1;
    do {
      if(i > 100) break;
      i = i * 2;
      } while<caret>(i % 10 != 0);
  }
}