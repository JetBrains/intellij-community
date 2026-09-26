// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
record R(int x, int y) {
}

class Main {
  public static void main(String[] args) {
    R r = new R(10, 20);
    System.out.println("x: " + r.x() + ", y: " + r.y());
  }
}
