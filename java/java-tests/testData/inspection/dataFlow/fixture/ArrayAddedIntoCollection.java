import java.util.*;

public class ArrayAddedIntoCollection {
  public static void main(String[] args) {
    int[] data = {-1};
    List<int[]> wrapped = new ArrayList<>();
    wrapped.add(wrap(data));
    wrapped.get(0)[0] = 0;
    if (data[0] == 0) {
      System.out.println("oops");
    }
  }

  static int[] wrap(int[] data) {
    return data;
  }
}
