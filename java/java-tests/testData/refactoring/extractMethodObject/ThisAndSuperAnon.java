public class XXX extends Base {
  int x = 5;

  public void con<caret>text() {
    int a = this.x;
    int b = super.y;
  }
}

class Base {
  int y = 7;
}
