public class ReturnValue {
  public int getX() {
    int y=0;
    return y;
  }

  interface I {
    int m();
  }

  public int getY() {
    I i = () -> {
      int x = 0;
      return x;
    }
    return i.m();
  }
}