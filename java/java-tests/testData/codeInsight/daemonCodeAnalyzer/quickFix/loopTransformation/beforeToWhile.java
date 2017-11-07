// "Specify loop bounds explicitly" "true"
class Main {
  public static void main(String[] args) {
    int i = 0;
    whi<caret>le(true) {
      i++;
      if(i >= 12) break;
    }
  }
}