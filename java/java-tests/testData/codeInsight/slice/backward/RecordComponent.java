class MainTest {
  record Rec(int <caret>x, int y) {
    Rec {}
  }

  public static void main(String[] args) {
    Rec rec = new Rec(<flown1>1, 2);
  }
}