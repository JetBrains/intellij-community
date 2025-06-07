// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
class R<caret> {
  final int x;
  final int y;

  R(int x, int y) {
    this.x = x;
    this.y = y;
  }

  int getX() {
    return x;
  }

  int getY() {
    return y;
  }
}

class Main {
  public static void main(String[] args) {
    R r = new R(10, 20);
    System.out.println("x: " + r.getX() + ", y: " + r.getY());
  }
}
