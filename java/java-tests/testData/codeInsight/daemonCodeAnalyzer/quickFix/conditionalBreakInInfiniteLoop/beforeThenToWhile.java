// "Move condition to loop" "true-preview"
class Main {
  public static void main(String[] args) {
    int i = 0;
    while<caret> (true) {
      if (i < 13) {
        i++;
      } else {
        break;
      }
      System.out.println("asd");
    }
  }
}