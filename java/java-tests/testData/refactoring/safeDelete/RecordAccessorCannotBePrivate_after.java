interface X1 {
}
record Point(int x1, int y) implements X1 {
  @Override
  public int x1() {
    System.out.println(x1);
    return x1;
  }

  public static void main(String[] args) {
    Point tmp = new Point(1, 1);
    int i = tmp.x1();
  }
}