// "Specify loop bounds explicitly" "false"
class Main {
  public static void main(String[] args) {
    int i = 0;
    whi<caret>le(true) {
      i++;
      int j = i;
      if(j >= 12) break;
    }
  }
}