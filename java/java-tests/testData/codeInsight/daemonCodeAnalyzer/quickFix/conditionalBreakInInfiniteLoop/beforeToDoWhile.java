// "Move condition to loop" "true-preview"
class Main {
  public static void main(String[] args) {
    int i = 0;
    while<caret>(true) {
      i++;
      if(i >= 12) break;
    }
  }
}