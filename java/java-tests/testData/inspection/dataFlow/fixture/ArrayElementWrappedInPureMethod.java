import java.util.*;

public class ArrayElementWrappedInPureMethod {
  public static void main(String[] args) {
    int[] data = {-1};
    List<int[]> wrapped = wrap(data);
    wrapped.get(0)[0] = 0;
    if (data[0] == 0) {
      System.out.println("oops");
    }
  }

  static List<int[]> wrap(int[] data) {
    return List.of(data);
  }
}
