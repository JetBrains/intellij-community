record Point(int x, int y) /*implements Tmp*/ {
  @Override
  public int x() { // rename this accessor
    System.out.println(x);
    return x;
  }

  public static void main(String[] args) {
    Point tmp = new Point(1, 1);
    int i = tmp.x();
  }
}