
class ShiftParam {
  static int getDepth(int parallelism, int size) {
    int depth = 0;

    while ((parallelism >>= 3) > 0 && (size >>= 2) > 0) {
      depth -= 2;
    }
    return depth;
  }
}

class Run {
  public static void main(String[] args) {
    System.out.println(ShiftParam.getDepth(15, 80));
  }
}
