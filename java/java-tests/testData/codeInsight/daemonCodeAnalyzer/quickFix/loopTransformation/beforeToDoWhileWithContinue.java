// "Specify loop bounds explicitly" "false"
class Main {
  public static void main(String[] args) {
    int i = 0;
    whi<caret>le(true) {
      i++;
      if(i == 4) continue;
      if(i >= 12) break;
    }
  }
}