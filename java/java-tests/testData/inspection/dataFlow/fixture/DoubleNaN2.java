public class DoubleNaN2 {
  public static int compare(double lhs, double rhs) {
    if (lhs == rhs) {
      return 0;
    }
    if (lhs < rhs) {
      return -1;
    }

    if (lhs > rhs) {
      return 1;
    }

    if (Double.isNaN(lhs)) {
      return Double.isNaN(rhs) ? 0 : -1;
    }
    return 1;
  }
}