// "Move condition to loop" "true"
class Main {
  public static void main(String[] args) {
    int i = 0;
    while(true) {
      i++;
      if<caret>(i >= 12) break;
    }
  }
}